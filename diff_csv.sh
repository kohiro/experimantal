#!/bin/bash

# 引数チェック
if [ "$#" -ne 2 ]; then
    echo "Usage: $0 file1.csv file2.csv"
    exit 1
fi

FILE1="$1"
FILE2="$2"
KEY_COL=3
SAMPLE_LIMIT=10

# awkによる比較処理
awk -F, -v f2="$FILE2" -v key_col="$KEY_COL" -v limit="$SAMPLE_LIMIT" '
BEGIN {
    # 差分カウンタとサンプル配列の初期化は自動で行われるため不要
}

# FILE1の各行に対する処理
{
    k1 = $key_col
    
    # FILE2から行を読み込み、キーを合わせるループ (Merge Join的な処理)
    while (1) {
        # FILE2から1行読む
        status = (getline line2 < f2)
        if (status <= 0) {
            # FILE2が終了した場合、FILE1の残りは比較対象なしとして終了
            exit
        }
        
        # 配列に分割
        split(line2, arr2, ",")
        k2 = arr2[key_col]
        
        if (k2 < k1) {
            # FILE2のキーが小さい -> FILE2を進める (FILE1にあるがFILE2にないキーは無視する場合)
            # ※必要であればここで「FILE2にキー欠損」のログを出せる
            continue
        } else if (k2 > k1) {
            # FILE2のキーが追い越した -> FILE1を進める (FILE2にあるがFILE1にないキー)
            # 次のFILE1の行を読むためにこのブロックを抜ける
            next_file1 = 1
            break
        } else {
            # キー一致 (k1 == k2) -> 比較処理へ
            next_file1 = 0
            break
        }
    }
    
    if (next_file1) next

    # カラムごとの比較
    # NFは現在の行(FILE1)のカラム数
    max_col = (NF > length(arr2)) ? NF : length(arr2)
    
    for (i = 1; i <= max_col; i++) {
        val1 = $i
        val2 = arr2[i]
        
        if (val1 != val2) {
            # 差分がある場合
            diff_counts[i]++
            
            # サンプル数が上限未満なら保存
            if (diff_counts[i] <= limit) {
                # フォーマット: Key: Value1 -> Value2
                samples[i, diff_counts[i]] = k1 ": " val1 " -> " val2
            }
        }
    }
}

END {
    # 結果の出力
    print "=== Comparison Report ==="
    has_diff = 0
    
    # 記録されたカラム順にスキャン（最大カラム数は推測）
    for (i in diff_counts) {
        has_diff = 1
        print "--------------------------------------------------"
        printf "Column %d: %d differences found (showing first %d)\n", i, diff_counts[i], (diff_counts[i] < limit ? diff_counts[i] : limit)
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