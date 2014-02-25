describe('Shelob Services', function() {

    describe('API service', function() {

        var injector, $httpBackend, Token, API;

        beforeEach(module('shelobServices'));

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
            $httpBackend.expectGET('/api/v1.0/children/').respond(401);
            $httpBackend.expectGET('/json_new_token').respond({token: 'new_token'});
            $httpBackend.expectGET('/api/v1.0/children/').respond(200);

            var success = jasmine.createSpy();
            API.get('/children/').then(success);
            $httpBackend.flush();
            expect(success).toHaveBeenCalled();
        });

        it('should not request a second new token if successive API calls get a 401 response', function() {
            $httpBackend.whenGET('/json_token').respond('token');
            $httpBackend.whenGET('/json_new_token').respond('newtoken');
            // should only try twice
            $httpBackend.expectGET('/api/v1.0/children/').respond(401);
            $httpBackend.expectGET('/api/v1.0/children/').respond(401);
            // and then it should fail
            var failure = jasmine.createSpy();
            API.get('/children/').catch(failure);
            $httpBackend.flush();
            expect(failure).toHaveBeenCalledWith(401);
        });

        it('should fail without requesting a second token if API call gets a 500 response', function() {
            $httpBackend.whenGET('/json_token').respond('token');
            $httpBackend.expectGET('/api/v1.0/children/').respond(500);
            var failure = jasmine.createSpy();
            API.get('/children/').catch(failure);
            $httpBackend.flush();
            expect(failure).toHaveBeenCalledWith(500);
        });

        it('should fail API call with 500 status if getting a token fails', function() {
            $httpBackend.expectGET('/json_token').respond(500);
            var failure = jasmine.createSpy();
            API.get('/children/').catch(failure);
            $httpBackend.flush();
            expect(failure).toHaveBeenCalledWith(500);
        });
    });
});

