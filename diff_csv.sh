#!/bin/bash

# 引数チェック
if [ "$#" -ne 2 ]; then
    echo "Usage: $0 file1.csv[.gz] file2.csv[.gz]"
    exit 1
fi

FILE1="$1"
FILE2="$2"
KEY_COLS="1,3"   # 複合キーの列番号（要件に合わせて変更）
SAMPLE_LIMIT=10

# ---------------------------------------------------------
# ファイルオープン用コマンドの生成
# ---------------------------------------------------------

# FILE1用: 標準入力に流し込むためのコマンド
if [[ "$FILE1" =~ \.gz$ ]]; then
    CMD_INPUT1="gzip -dc \"$FILE1\""
else
    CMD_INPUT1="cat \"$FILE1\""
fi

# FILE2用: awk内部で実行するためのコマンド文字列
# (ファイル名にダブルクォートが含まれる場合のエスケープ処理を含む)
SAFE_FILE2=$(printf '%s' "$FILE2" | sed 's/"/\\"/g')
if [[ "$FILE2" =~ \.gz$ ]]; then
    CMD_INPUT2="gzip -dc \"$SAFE_FILE2\""
else
    CMD_INPUT2="cat \"$SAFE_FILE2\""
fi

# ---------------------------------------------------------
# メイン処理
# ---------------------------------------------------------
# evalを使って CMD_INPUT1 の結果を awk の標準入力に渡す
eval "$CMD_INPUT1" | awk -F, -v f2_cmd="$CMD_INPUT2" -v key_list="$KEY_COLS" -v limit="$SAMPLE_LIMIT" '
BEGIN {
    num_keys = split(key_list, k_idxs, ",")
    has_buffer = 0
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
    # 【変更点】 "< f2" ではなく "f2_cmd | getline" を使用
    if ((f2_cmd | getline dummy) <= 0) {
        print "Error: File 2 is empty or cannot be opened." > "/dev/stderr"
        exit 1
    }
    next
}

# --- データ行処理 ---
{
    k1 = build_key("", 0)
    
    while (1) {
        if (has_buffer == 0) {
            # 【変更点】 コマンドパイプから読み込み
            status = (f2_cmd | getline line2)
            if (status <= 0) {
                exit
            }
            buffered_line = line2
            has_buffer = 1
        }
        
        split(buffered_line, arr2, ",")
        k2 = build_key(arr2, 1)
        
        if (k2 < k1) {
            has_buffer = 0
            continue
        } else if (k2 > k1) {
            next
        } else {
            has_buffer = 0
            break
        }
    }

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
    # パイプを閉じる（作法として）
    close(f2_cmd)

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
'
