/**
 *
 */
$(function(){

    // フォームデータのアップロード処理
    var uploadChunk = function(fileName, fileKey, totalBytes, blob, chunks, chunkCount, progress, always) {
        // アップロードの進捗表示
        var xhr_func = function(){
            var XHR = $.ajaxSettings.xhr();
            XHR.upload.addEventListener('progress', function(e){
                chunks[chunkCount] = e.loaded;
                var doneBytes = 0;
                chunks.forEach(function(bytes) {doneBytes += bytes;});
                var progre = parseInt(doneBytes/totalBytes * 100);
				progress(e, progre);
            });
            return XHR;
        };

        // Ajaxでアップロード処理をするファイルへ内容渡す
        $.ajax({
            url: 'fileupload/upload',
            type: 'POST',
            data: blob,
            processData: false,
            contentType: 'application/octet-stream',
            headers: {
                'File-Name': fileName,
                'File-Key': fileKey,
                'Chunk-Index': chunkCount,
                'Chunk-Total': chunks.length
            },
            xhr : xhr_func
        }).always(function(arg1, textStatus, arg2) {
			// jqXHR.always(function(data|jqXHR, textStatus, jqXHR|errorThrown ) {});
            console.log(textStatus);
			always(xhr_func, textStatus);
		});
    };

    // ファイルのアップロード処理
    var uploadFile = function(file) {
        // 分割するサイズ(byte)
        var chunkSize = 1 * 1024 * 1024;
        // 選択されたファイルの総容量を取得
        var totalBytes = file.size;
        // ファイル名
        var fileName = file.name;
        // チャンク分割数
        var chunkCount = Math.ceil(totalBytes / chunkSize);
        // 識別キー
        var fileKey = createUuid();
        var readBytes = 0;
        var chunks = [];
		// chunkごとの送信バイト数の初期値設定
        for (var i = 0; i < chunkCount; i++) chunks.push(0);
        // チャンクサイズごとにスライスしながら読み込み
        $.each(chunks, function(index) {
            // stopをオーバーして指定した場合は自動的に切り詰められる
            var blob = file.slice(readBytes, readBytes += chunkSize);
            var reader = new FileReader();
            reader.onloadend = function(evt) {
                // 読み取り完了のイベントだけキャッチ
                if (evt.target.readyState != FileReader.DONE) {
                    return;
                }
                if (evt.target.readyState != FileReader.EMPTY) {
					//TODO:
                }
                // 読み取ったデータを取り出し
                var blob = evt.target.result;
				var progress = function(ev, progre){};
				var always = function(data){};
                uploadChunk(fileName, fileKey, totalBytes, blob, chunks, index, progress, always);
            };
            reader.readAsArrayBuffer(blob);
        });
    };

    var createUuid = function() {
        var uuid = "", i, random;
        for (i = 0; i < 32; i++) {
            random = Math.random() * 16 | 0;
            if (i == 8 || i == 12 || i == 16 || i == 20) {
                uuid += "-";
            }
            uuid += (i == 12 ? 4 : (i == 16 ? (random & 3 | 8) : random)).toString(16);
        }
        return uuid;
    };

	// ----------- //
    // ファイルドロップ時の処理
    $('#drag-area').on('drop', function(e){
        // デフォルトの挙動を停止
        e.preventDefault();

        // ファイル情報を取得
        var files = e.originalEvent.dataTransfer.files;
        uploadFile(files[0]);


    // デフォルトの挙動を停止　これがないと、ブラウザーによりファイルが開かれる
    }).on('dragenter', function(){
        return false;
    }).on('dragover', function(){
        return false;
    });
    // ボタンを押した時の処理
    $('#btn').on('click', function() {
        // ダミーボタンとinput[type="file"]を連動
        $('#file_selecter').click();
    });
    $('#file_selecter').on('change', function(){
        // ファイル情報を取得
        uploadFile(this.files[0]);
    });

});
