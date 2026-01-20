#!/bin/bash

if [ "$#" -ne 2 ]; then
    echo "Usage: $0 file1.csv file2.csv"
    exit 1
fi

FILE1="$1"
FILE2="$2"
KEY_COLS="1,3"   # 複合キーの列番号（要件に合わせて変更）
SAMPLE_LIMIT=10

awk -F, -v f2="$FILE2" -v key_list="$KEY_COLS" -v limit="$SAMPLE_LIMIT" '
BEGIN {
    num_keys = split(key_list, k_idxs, ",")
    has_buffer = 0  # FILE2の読み込みバッファがあるかどうかのフラグ
}

function build_key(arr_or_fld, is_line_parse,    k, i, val) {
    k = ""
    for (i = 1; i <= num_keys; i++) {
        col_idx = k_idxs[i]
        val = (is_line_parse) ? arr_or_fld[col_idx] : $(col_idx)
        k = k (i == 1 ? "" : ",") val
    }
    return k
}

# --- ヘッダー処理 ---
NR == 1 {
    for (i = 1; i <= NF; i++) header[i] = $i
    
    # FILE2のヘッダーを読み捨て
    if ((getline dummy < f2) <= 0) {
        print "Error: File 2 is empty." > "/dev/stderr"
        exit 1
    }
    next
}

# --- データ行処理 ---
{
    k1 = build_key("", 0)
    
    while (1) {
        # バッファがなければFILE2から新規読み込み
        if (has_buffer == 0) {
            status = (getline line2 < f2)
            if (status <= 0) {
                # FILE2が終わったらFILE1の残りは無視して終了（またはループ継続）
                exit
            }
            # 読んだ内容をバッファとして保持
            buffered_line = line2
            has_buffer = 1
        }
        
        # バッファされている行を解析
        split(buffered_line, arr2, ",")
        k2 = build_key(arr2, 1)
        
        if (k2 < k1) {
            # FILE2がまだ後ろにいる -> バッファを消費して次を読む
            has_buffer = 0
            continue
        } else if (k2 > k1) {
            # FILE2が進みすぎた（FILE1にないキー）
            # バッファ（現在のFILE2の行）は保持したまま（has_buffer=1）、FILE1を進める
            next
        } else {
            # キー一致 -> バッファを消費して比較へ
            has_buffer = 0
            break
        }
    }

    # ここに来るのは k1 == k2 の場合のみ
    max_col = (NF > length(arr2)) ? NF : length(arr2)
    
    for (i = 1; i <= max_col; i++) {
        val1 = $i
        val2 = arr2[i]
        
        if (val1 != val2) {
            diff_counts[i]++
            if (diff_counts[i] <= limit) {
                samples[i, diff_counts[i]] = k1 ": " val1 " -> " val2
            }
        }
    }
}

END {
    print "=== CSV Comparison Report (Keys: " key_list ") ==="
    has_diff = 0
    
    for (i in diff_counts) {
        has_diff = 1
        col_name = (i in header) ? header[i] : "Unknown"
        
        print "--------------------------------------------------"
        printf "Column %d [%s]: %d diffs (showing first %d)\n", i, col_name, diff_counts[i], (diff_counts[i] < limit ? diff_counts[i] : limit)
        print "--------------------------------------------------"
        
        for (j = 1; j <= limit; j++) {
            if ((i, j) in samples) {
                print samples[i, j]
            }
        }
        print ""
    }
    
    if (has_diff == 0) print "No differences found."
}
' "$FILE1"
        if (val1 != val2) {
            diff_counts[i]++
            if (diff_counts[i] <= limit) {
                samples[i, diff_counts[i]] = k1 ": " val1 " -> " val2
            }
        }
    }
}

END {
    print "=== CSV Comparison Report (Keys: " key_list ") ==="
    has_diff = 0
    
    for (i in diff_counts) {
        has_diff = 1
        col_name = (i in header) ? header[i] : "Unknown"
        
        print "--------------------------------------------------"
        printf "Column %d [%s]: %d diffs (showing first %d)\n", i, col_name, diff_counts[i], (diff_counts[i] < limit ? diff_counts[i] : limit)
        print "--------------------------------------------------"
        
        for (j = 1; j <= limit; j++) {
            if ((i, j) in samples) {
                print samples[i, j]
            }
        }
        print ""
    }
    
    if (has_diff == 0) print "No differences found."
}
' "$FILE1"
