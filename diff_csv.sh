#!/bin/bash

# 引数チェック
if [ "$#" -ne 3 ]; then
    echo "Usage: $0 <file1.csv[.gz]> <file2.csv[.gz]> <key_cols>"
    echo "Example: $0 old_data.csv new_data.csv.gz 1,3"
    exit 1
fi

FILE1="$1"
FILE2="$2"
KEY_COLS="$3"
SAMPLE_LIMIT=10

# ---------------------------------------------------------
# ファイルオープン用コマンドの生成
# ---------------------------------------------------------
if [[ "$FILE1" =~ \.gz$ ]]; then
    CMD_INPUT1="gzip -dc \"$FILE1\""
else
    CMD_INPUT1="cat \"$FILE1\""
fi

SAFE_FILE2=$(printf '%s' "$FILE2" | sed 's/"/\\"/g')
if [[ "$FILE2" =~ \.gz$ ]]; then
    CMD_INPUT2="gzip -dc \"$SAFE_FILE2\""
else
    CMD_INPUT2="cat \"$SAFE_FILE2\""
fi

# ---------------------------------------------------------
# メイン処理 (awk)
# ---------------------------------------------------------
eval "$CMD_INPUT1" | awk -F, -v f2_cmd="$CMD_INPUT2" -v key_list="$KEY_COLS" -v limit="$SAMPLE_LIMIT" '
BEGIN {
    num_keys = split(key_list, k_idxs, ",")
    has_buffer = 0
    file2_eof = 0
    only_in_file1_count = 0
    only_in_file2_count = 0
}

# --- 関数: ダブルクォートの除去 ---
function unquote(v) {
    gsub(/^"|"$/, "", v)
    return v
}

# --- 関数: 複合キーの作成 ---
function build_key(arr_or_fld, is_line_parse,    k, i, val) {
    k = ""
    for (i = 1; i <= num_keys; i++) {
        col_idx = k_idxs[i]
        val = (is_line_parse) ? arr_or_fld[col_idx] : $(col_idx)
        val = unquote(val)
        k = k (i == 1 ? "" : ",") val
    }
    return k
}

# --- ヘッダー処理 ---
NR == 1 {
    for (i = 1; i <= NF; i++) {
        header[i] = unquote($i)
    }
    
    if ((f2_cmd | getline dummy) <= 0) {
        file2_eof = 1 # FILE2が空の場合
    }
    next
}

# --- データ行処理 ---
{
    k1 = build_key("", 0)
    next_file1 = 0

    # FILE2が既に終わっている場合、以降のFILE1の行は全て「FILE1のみ」
    if (file2_eof) {
        only_in_file1_count++
        if (only_in_file1_count <= limit) {
            only_in_file1_samples[only_in_file1_count] = k1 " : " $0
        }
        next
    }

    while (1) {
        if (has_buffer == 0) {
            status = (f2_cmd | getline line2)
            if (status <= 0) {
                # FILE2が終わった場合
                file2_eof = 1
                only_in_file1_count++
                if (only_in_file1_count <= limit) {
                    only_in_file1_samples[only_in_file1_count] = k1 " : " $0
                }
                next_file1 = 1
                break
            }
            buffered_line = line2
            has_buffer = 1
        }
        
        split(buffered_line, arr2, ",")
        k2 = build_key(arr2, 1)
        
        if (k2 < k1) {
            # FILE2にしか存在しないキー
            only_in_file2_count++
            if (only_in_file2_count <= limit) {
                only_in_file2_samples[only_in_file2_count] = k2 " : " buffered_line
            }
            has_buffer = 0
            continue
        } else if (k2 > k1) {
            # FILE1にしか存在しないキー
            only_in_file1_count++
            if (only_in_file1_count <= limit) {
                only_in_file1_samples[only_in_file1_count] = k1 " : " $0
            }
            # バッファは維持したまま、次のFILE1の行へ
            next_file1 = 1
            break
        } else {
            # キー一致 -> 列の比較へ
            has_buffer = 0
            break
        }
    }

    if (next_file1) next

    # --- キーが一致した行の列比較 ---
    max_col = (NF > length(arr2)) ? NF : length(arr2)
    
    for (i = 1; i <= max_col; i++) {
        val1 = unquote($i)
        val2 = unquote(arr2[i])
        
        if (val1 != val2) {
            diff_counts[i]++
            if (diff_counts[i] <= limit) {
                samples[i, diff_counts[i]] = k1 ": " val1 " -> " val2
            }
        }
    }
}

# --- 終了処理 ---
END {
    # FILE1が先に終わった場合、FILE2の残りをすべて「FILE2のみ」として処理
    if (!file2_eof) {
        if (has_buffer) {
            split(buffered_line, arr2, ",")
            k2 = build_key(arr2, 1)
            only_in_file2_count++
            if (only_in_file2_count <= limit) {
                only_in_file2_samples[only_in_file2_count] = k2 " : " buffered_line
            }
        }
        while ((f2_cmd | getline line2) > 0) {
            split(line2, arr2, ",")
            k2 = build_key(arr2, 1)
            only_in_file2_count++
            if (only_in_file2_count <= limit) {
                only_in_file2_samples[only_in_file2_count] = k2 " : " line2
            }
        }
    }
    close(f2_cmd)

    # ---------------------------------------------------------
    # レポート出力
    # ---------------------------------------------------------
    print "=================================================="
    print "=== CSV Comparison Report (Keys: " key_list ") ==="
    print "=================================================="
    print ""

    # [セクション1: 行単位の差分]
    print "--- [Section 1: Row-level Differences] ---"
    if (only_in_file1_count == 0 && only_in_file2_count == 0) {
        print "No row-level differences (All keys matched)."
    } else {
        if (only_in_file1_count > 0) {
            print "Rows ONLY in FILE 1: " only_in_file1_count " (showing first " (only_in_file1_count < limit ? only_in_file1_count : limit) ")"
            for (i = 1; i <= limit; i++) {
                if (i in only_in_file1_samples) print "  " only_in_file1_samples[i]
            }
            print ""
        }
        if (only_in_file2_count > 0) {
            print "Rows ONLY in FILE 2: " only_in_file2_count " (showing first " (only_in_file2_count < limit ? only_in_file2_count : limit) ")"
            for (i = 1; i <= limit; i++) {
                if (i in only_in_file2_samples) print "  " only_in_file2_samples[i]
            }
            print ""
        }
    }
    print ""

    # [セクション2: 列単位の差分]
    print "--- [Section 2: Column-level Differences (for matching keys)] ---"
    has_col_diff = 0
    for (i in diff_counts) {
        has_col_diff = 1
        col_name = (i in header) ? header[i] : "Unknown"
        
        print "Column " i " [" col_name "]: " diff_counts[i] " diffs (showing first " (diff_counts[i] < limit ? diff_counts[i] : limit) ")"
        for (j = 1; j <= limit; j++) {
            if ((i, j) in samples) {
                print "  " samples[i, j]
            }
        }
        print ""
    }
    
    if (has_col_diff == 0) {
        print "No column-level differences found in matching keys."
    }
}
'
