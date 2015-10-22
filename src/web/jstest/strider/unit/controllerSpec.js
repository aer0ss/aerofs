describe('Strider Controllers', function () {

    beforeEach(module('ngSanitize'));
    beforeEach(module('striderControllers'));


    describe('UsersController', function () {

        var $controller,
            $httpBackend,
            createController,
            listUsersData;

        beforeEach(inject(function ($injector) {
            $rootScope = $injector.get('$rootScope');
            $controller = $injector.get('$controller');
            $httpBackend = $injector.get('$httpBackend');

            modalObject = {
                result: {then: jasmine.createSpy('modalObject.result.then')}
            };
            modal = jasmine.createSpy('modal');
            modal.open = jasmine.createSpy('modal.open').andReturn(modalObject);

            createController = function () {
                controller = $controller('UsersController', {
                    $scope: $rootScope,
                    $rootscope: $rootScope,
                    $modal: modal
                });
                $httpBackend.flush();
                return controller
            }

            listUsersData = {
                data: [
                    {name: 'test', email: 'test@test.com'},
                    {name: 'test1', email: 'test1@test.com'},
                    {name: 'test2', email: 'test2@test.com'}],
                total: 3,
                pagination_limit: 20,
                use_restricted: true,
                me: {}
            };

            $httpBackend
                .whenGET(userDataURL)
                .respond(listUsersData);
            $httpBackend
                .expectGET(userDataURL + '?offset=0')
                .respond(listUsersData);

            createController();
        }));

        afterEach(function() {
            $httpBackend.verifyNoOutstandingExpectation();
            $httpBackend.verifyNoOutstandingRequest();
        });

        describe('getUserData', function () {

            it('should get the users and set up scope', function () {
                expect($rootScope.substring).toBeUndefined;
                expect($rootScope.users.length).toBe(listUsersData.data.length);
                expect($rootScope.paginationInfo.total).toBe(listUsersData.total);
                expect($rootScope.paginationInfo.limit).toBe(listUsersData.pagination_limit);
            });

            it('should should set the cache', function () {
                expect($rootScope.initialLoad.users.length).toBe(listUsersData.data.length);
                expect($rootScope.initialLoad.total).toBe(listUsersData.total);
            });

            it('should update user count message', function () {
                expect($rootScope.userCountMessage).toBe('User count: ' + listUsersData.total);
            });

        });

        describe('$scope.updateUsers', function() {

            it('should update internal scope values', function () {
                //users, total, substring
                $rootScope.updateUsers(listUsersData.data.slice(0,1), 1, 'foo');
                expect($rootScope.users.length).toBe(1);
                expect($rootScope.paginationInfo.total).toBe(1);
                expect($rootScope.substring).toBe('foo');
                expect($rootScope.userCountMessage).toBe('User count: 1')
            });

        });

        describe('$scope.restore', function () {

            it('should update internal scope values from cache if cache', function () {
                $rootScope.substring = 'foo';
                $rootScope.paginationInfo.offset = 5;
                $rootScope.users = [];
                //Expect it to reload from cache
                $rootScope.restore();
                expect($rootScope.users.length).toBe($rootScope.initialLoad.users.length);
                expect($rootScope.paginationInfo.total).toBe($rootScope.initialLoad.total);
                expect($rootScope.substring).toBe('');
                expect($rootScope.paginationInfo.offset).toBe(0);
                expect($rootScope.userCountMessage).toBe('User count: ' + $rootScope.initialLoad.total);
            });

            it('should update internal scope values from request if no cache', function () {
                $rootScope.substring = 'foo';
                $rootScope.paginationInfo.offset = 5;
                $rootScope.users = [];
                $rootScope.initialLoad = {};
                //Expect it to reload from http get to list users
                $rootScope.restore();
                $httpBackend.expectGET(userDataURL + '?offset=0').respond(listUsersData);
                $httpBackend.flush();
                expect($rootScope.users.length).toBe($rootScope.initialLoad.users.length);
                expect($rootScope.paginationInfo.total).toBe($rootScope.initialLoad.total);
                expect($rootScope.substring).toBe('');
                expect($rootScope.paginationInfo.offset).toBe(0);
                expect($rootScope.userCountMessage).toBe('User count: ' + $rootScope.initialLoad.total);
            });
        });

        describe('$scope.paginationInfo.callback', function () {

            it('should reset offset and substring', function () {
                var offset = 3;
                var substring = 'bar';
                $rootScope.paginationInfo.callback(offset, substring);
                expect($rootScope.paginationInfo.offset).toBe(offset);
                expect($rootScope.substring).toBe(substring);

                $httpBackend.expectGET(userDataURL + '?offset=' + offset + '&substring=' + substring).respond(listUsersData);
                $httpBackend.flush()
            });

            it('should reset offset', function () {
                var offset = 3;
                $rootScope.paginationInfo.callback(offset);
                expect($rootScope.paginationInfo.offset).toBe(offset);
                expect($rootScope.substring).toBe('');

                $httpBackend.expectGET(userDataURL + '?offset=' + offset).respond(listUsersData);
                $httpBackend.flush()
            });
        });
    });
});