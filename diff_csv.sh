#!/bin/bash

# 引数チェック
if [ "$#" -ne 2 ]; then
    echo "Usage: $0 file1.csv file2.csv"
    exit 1
fi

FILE1="$1"
FILE2="$2"

# ---------------------------------------------------------
# 設定: 複合キーとする列番号をカンマ区切りで指定
# 例: "1,3" なら 1列目と3列目を組み合わせてキーにします
# ---------------------------------------------------------
KEY_COLS="1,3"
SAMPLE_LIMIT=10

# awkによる比較処理
awk -F, -v f2="$FILE2" -v key_list="$KEY_COLS" -v limit="$SAMPLE_LIMIT" '
BEGIN {
    # キー列のリストを配列に分解 (k_idxs[1]=1, k_idxs[2]=3 ...)
    num_keys = split(key_list, k_idxs, ",")
}

# ---------------------------------------------------------
# 関数: 指定された列から複合キー文字列を作成する
# ---------------------------------------------------------
function build_key(arr_or_fld, is_line_parse,    k, i, val) {
    k = ""
    for (i = 1; i <= num_keys; i++) {
        col_idx = k_idxs[i]
        
        if (is_line_parse) {
            # ファイル2(配列)からの取得
            val = arr_or_fld[col_idx]
        } else {
            # ファイル1(現在のフィールド)からの取得 ($1, $3...)
            val = $(col_idx)
        }
        
        # キーをカンマで連結 (例: "Val1,Val3")
        # ソート順に影響するため、元ファイルと同じ区切り文字を使うのが無難
        k = k (i == 1 ? "" : ",") val
    }
    return k
}

# ---------------------------------------------------------
# 1行目（ヘッダー）の処理
# ---------------------------------------------------------
NR == 1 {
    for (i = 1; i <= NF; i++) {
        header[i] = $i
    }
    
    # FILE2のヘッダー空読み
    if ((getline dummy < f2) <= 0) {
        print "Error: File 2 is empty." > "/dev/stderr"
        exit 1
    }
    next
}

# ---------------------------------------------------------
# 2行目以降（データ行）の処理
# ---------------------------------------------------------
{
    # FILE1の複合キーを作成
    k1 = build_key("", 0)
    
    # FILE2から行を読み込み、キーを合わせる
    while (1) {
        status = (getline line2 < f2)
        if (status <= 0) exit
        
        split(line2, arr2, ",")
        
        # FILE2の複合キーを作成
        k2 = build_key(arr2, 1)
        
        if (k2 < k1) {
            continue
        } else if (k2 > k1) {
            next_file1 = 1
            break
        } else {
            next_file1 = 0
            break
        }
    }
    
    if (next_file1) next

    # カラム比較
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
