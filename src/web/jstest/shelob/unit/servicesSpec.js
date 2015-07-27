describe('Shelob Services', function() {

    beforeEach(module('shelobServices'));

    describe('API service', function() {

        var injector, $httpBackend, Token, API;
        var tokenRoute = /\/json_token(\?t=[01]?.?\d*)?/;

        beforeEach(inject(function($injector) {
            injector = $injector;
            $httpBackend = injector.get('$httpBackend');
            Token        = injector.get('Token');
            API          = injector.get('API');
        }));

        it('should request a new token if an API call gets a 401 response', function() {
            Token.get = function() {
                return {then: function(succeed, fail) { succeed('stale_token'); }};
            };
            $httpBackend.expectGET('/api/v1.2/folders/root/children', function(headers) {
                return headers.Authorization == 'Bearer stale_token';
            }).respond(401);
            $httpBackend.expectPOST('/json_new_token').respond({token: 'new_token'});
            $httpBackend.expectGET('/api/v1.2/folders/root/children', function(headers) {
                return headers.Authorization == 'Bearer new_token';
            }).respond(200);

            var success = jasmine.createSpy();
            API.get('/folders/root/children').then(success);
            $httpBackend.flush();
            expect(success).toHaveBeenCalled();
        });

        it('should not request a second new token if successive API calls get a 401 response', function() {
            $httpBackend.whenPOST(tokenRoute).respond('token');
            $httpBackend.whenPOST('/json_new_token').respond('newtoken');
            // should only try twice
            $httpBackend.expectGET('/api/v1.2/folders/root/children').respond(401);
            $httpBackend.expectGET('/api/v1.2/folders/root/children').respond(401);
            // and then it should fail
            var failure = jasmine.createSpy();
            API.get('/folders/root/children').catch(failure);
            $httpBackend.flush();
            expect(failure).toHaveBeenCalledWith({status: 401});
        });

        it('should fail without requesting a second token if API call gets a 500 response', function() {
            $httpBackend.whenPOST(tokenRoute).respond('token');
            $httpBackend.expectGET('/api/v1.2/folders/root/children').respond(500);
            var failure = jasmine.createSpy();
            API.get('/folders/root/children').catch(failure);
            $httpBackend.flush();
            expect(failure).toHaveBeenCalledWith({status: 500});
        });

        it('should fail API call with 500 status if getting a token fails', function() {
            $httpBackend.expectPOST(tokenRoute).respond(500);
            var failure = jasmine.createSpy();
            API.get('/folders/root/children').catch(failure);
            $httpBackend.flush();
            expect(failure).toHaveBeenCalledWith({status: 500});
        });

        describe('Chunked upload', function() {
            var blob;
            var contentBytes;
            beforeEach(function(){
                // mock file
                var content = "this is the content of a file";
                // contents as blob
                var BlobBuilder = window.BlobBuilder || window.WebKitBlobBuilder || window.MozBlobBuilder || window.MSBlobBuilder;
                var bb = new BlobBuilder();
                bb.append(content);
                blob = bb.getBlob();
                // contents as array buffer
                var r = new FileReader();
                r.readAsArrayBuffer(blob);
                contentBytes = r.result;
                // flush the readAsArrayBuffer calls
                spyOn(window, 'FileReader').andReturn({readAsArrayBuffer: function(blob) {
                    var event = {target: {result: contentBytes}};
                    this.onload(event);
                }});

                // assume token acquisition succeeds
                $httpBackend.whenPOST(tokenRoute).respond('token');
                $httpBackend.whenPOST('/json_new_token').respond('newtoken');
            });

            it('should upload small file in a single chunk', function() {
                // lay out expected HTTP calls
                $httpBackend.expectPUT('/api/v1.2/files/abc123/content', contentBytes).respond(200);

                var succeed = jasmine.createSpy();
                API.chunkedUpload('abc123', blob).then(succeed);
                $httpBackend.flush();
                expect(succeed).toHaveBeenCalled();
            });

            it('should upload larger file in multiple chunks', function() {
                // lay out expected HTTP calls
                $httpBackend.expectPUT('/api/v1.2/files/abc123/content').respond(200, null, {'Upload-ID': 'idididid'});
                $httpBackend.expectPUT('/api/v1.2/files/abc123/content', null, function(headers) {
                    // make sure the second request uses the Upload-ID from the first response
                    return headers['Upload-ID'] == 'idididid';
                }).respond(200, null, {'Upload-ID': 'idididid'});
                $httpBackend.whenPUT('/api/v1.2/files/abc123/content', null, function(headers) {
                    // make all future requests use the Upload-ID from the first response
                    return headers['Upload-ID'] == 'idididid';
                }).respond(200, null, {'Upload-ID': 'idididid'});

                // make call (note chunkSize is set to 10 bytes, so multiple calls are expected)
                var succeed = jasmine.createSpy();
                API.chunkedUpload('abc123', blob, 10).then(succeed);
                $httpBackend.flush();
                expect(succeed).toHaveBeenCalled();
            });

            it('should call the progress callback between chunks', function() {
                // lay out expected HTTP calls
                $httpBackend.whenPUT('/api/v1.2/files/abc123/content').respond(200, null, {'Upload-ID': 'idididid'});

                // make call (note chunkSize is set to 10 bytes, so multiple calls are expected)
                var succeed = jasmine.createSpy();
                var progress = jasmine.createSpy();
                API.chunkedUpload('abc123', blob, 10).then(succeed, console.log, progress);
                $httpBackend.flush();
                expect(succeed).toHaveBeenCalled();
                expect(progress.callCount).toBeGreaterThan(1);
                // make sure progress was made between the first and second calls
                expect(progress.argsForCall[1][0].progress).toBeGreaterThan(progress.argsForCall[0][0].progress);
            });

            it('should call the failure callback when a request fails', function() {
                // lay out expected HTTP calls
                $httpBackend.expectPUT('/api/v1.2/files/abc123/content').respond(200, null, {'Upload-ID': 'idididid'});
                $httpBackend.expectPUT('/api/v1.2/files/abc123/content').respond(503);

                // make call (note chunkSize is set to 10 bytes, so multiple calls are expected)
                var failure = jasmine.createSpy();
                API.chunkedUpload('abc123', blob, 10).catch(failure);
                $httpBackend.flush();
                expect(failure).toHaveBeenCalledWith({reason: 'upload', status: 503});
            });
        });
    });
});
