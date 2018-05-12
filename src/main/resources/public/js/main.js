var stompClient = null;
var success = true;
var message;
var clipboard = new ClipboardJS('.copy-js');
var copyBtn = $('.copy-js');

// Various event listeners
connect();

clipboard.on('success', function(e) {
    copyBtn.attr('data-balloon', 'Copied!');

    copyBtn.mouseout(function(){
        copyBtn.removeAttr('data-balloon');
    });
});


function connect() {
    var socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, function (frame) {
        console.log('Connected: ' + frame);
        stompClient.subscribe('/topic/crackresults', function (message) {
            $('#cracked-text').text(message.body);
            setTimeout(closeMessage, 2000);
        });
    });
}


// Dropzone configuration options
// noinspection JSAnnotator
Dropzone.options.uploadForm = {
    previewTemplate: `
        <div class="card upload dz-preview dz-file-preview">
            <div class="card-body">
                <div class="dz-details">
                    <h1 class="h6 text-secondary dz-filename"><span data-dz-name></span></h1>
                </div>
                <div class="butn"><button type="button">Upload</button></div>
            </div>
        </div>
    `,
    clickable: '.upload-link',
    maxFilesize: 1,
    createImageThumbnails: false,
    autoProcessQueue: false,
    parallelUploads: 1,
    acceptedFiles: ".txt",
    init: function() {
        var dropz = this;

        dropz.on("addedfile", function(file) {
            // Only allow single file upload. Old files are replaced with new ones.
            if (dropz.files[1]!=null){
                dropz.removeFile(dropz.files[0]);
            }

            $('.dz-message').hide();

            // Hook up the start button
            $('.butn').on('click', function( event ) {
                event.preventDefault();
                $('.butn').addClass('loading');
                dropz.processQueue(file);
            });
        });

        dropz.on("error", function(file, errorMessage) {
            console.log(errorMessage);
            success = false;

            $('.dz-error-message').text(errorMessage);
            mysteryMessage();
        });

        dropz.on("success", function(file, data) {
            console.log('Successfully uploaded file: ' + data.fileName);

            success = true;
            setTimeout(mysteryMessage, 1000);
            $('#downloadLink').html('<a class="font-weight-bold" href="' + data.fileDownloadUri + '" target="_blank">Download</a>')
        });
    }
};

function mysteryMessage(){
    message = (success) ? 'success' : 'error';
    $('.message-' + message).addClass('active');
    setTimeout(function(){
        $('.butn').removeClass('loading');
    }, 500);
    $('.close').on('click', closeMessage);
}

function closeMessage() {
    if (success) {
        $('.dz-preview').hide();
        $('.file-contents').show();
    } else {
        $('.dz-preview').hide();
        $('.dz-message').show();
    }

    $('.message-' + message).removeClass('active');
}