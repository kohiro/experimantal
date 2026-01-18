#!/bin/bash

# 引数チェック
if [ "$#" -ne 2 ]; then
    echo "Usage: $0 file1.csv file2.csv"
    exit 1
fi

FILE1="$1"
FILE2="$2"
KEY_COL=3        # キーの列番号（要件に合わせて変更可能）
SAMPLE_LIMIT=10  # 1項目あたりのサンプル出力数

# awkによる比較処理
awk -F, -v f2="$FILE2" -v key_col="$KEY_COL" -v limit="$SAMPLE_LIMIT" '
# ---------------------------------------------------------
# 1行目（ヘッダー）の処理
# ---------------------------------------------------------
NR == 1 {
    # FILE1のヘッダーを配列に保存
    for (i = 1; i <= NF; i++) {
        header[i] = $i
    }
    
    # FILE2のヘッダーも読み飛ばしてデータ開始位置を合わせる
    if ((getline dummy < f2) <= 0) {
        print "Error: File 2 is empty or cannot be read." > "/dev/stderr"
        exit 1
    }
    
    # 次の行（データ行）へ
    next
}

# ---------------------------------------------------------
# 2行目以降（データ行）の処理
# ---------------------------------------------------------
{
    k1 = $key_col
    
    # FILE2から行を読み込み、キーを合わせるループ
    while (1) {
        # FILE2から1行読む
        status = (getline line2 < f2)
        if (status <= 0) {
            # FILE2終了
            exit
        }
        
        # 配列に分割
        split(line2, arr2, ",")
        k2 = arr2[key_col]
        
        if (k2 < k1) {
            # FILE2のキーが小さい -> FILE2を進める
            continue
        } else if (k2 > k1) {
            # FILE2が進みすぎた -> FILE1を進める
            next_file1 = 1
            break
        } else {
            # キー一致 -> 比較へ
            next_file1 = 0
            break
        }
    }
    
    if (next_file1) next

    # カラムごとの比較
    max_col = (NF > length(arr2)) ? NF : length(arr2)
    
    for (i = 1; i <= max_col; i++) {
        val1 = $i
        val2 = arr2[i]
        
        if (val1 != val2) {
            diff_counts[i]++
            
            if (diff_counts[i] <= limit) {
                # サンプル保存
                samples[i, diff_counts[i]] = k1 ": " val1 " -> " val2
            }
        }
    }
}

# ---------------------------------------------------------
# 結果出力
# ---------------------------------------------------------
END {
    print "=== CSV Comparison Report ==="
    has_diff = 0
    
    # 記録されたカラム順にスキャン
    for (i in diff_counts) {
        has_diff = 1
        col_name = (i in header) ? header[i] : "Unknown"
        
        print "--------------------------------------------------"
        # 列番号に加えて、ヘッダー名を表示
        printf "Column %d [%s]: %d diffs (showing first %d)\n", i, col_name, diff_counts[i], (diff_counts[i] < limit ? diff_counts[i] : limit)
        print "--------------------------------------------------"
        
        for (j = 1; j <= limit; j++) {
            if ((i, j) in samples) {
                print samples[i, j]
            }
        }
        print ""
    }
    
    if (has_diff == 0) {
        print "No differences found in matching keys."
    }
}
' "$FILE1"
