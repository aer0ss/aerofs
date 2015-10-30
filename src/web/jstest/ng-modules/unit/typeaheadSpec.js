describe('Typeahead', function () {

    beforeEach(module('typeahead'));
    beforeEach(module('templates'));

    describe('aeroListTypeahead', function () {
        var $compile,
            $rootScope,
            $httpBackend,
            listUsersData,
            listUsersDataFiltered,
            element;

        var triggerKeyDown = function (element, keyCode) {
            var e = $.Event("keydown");
            e.which = keyCode;
            element.trigger(e);
        };

        beforeEach(inject(function($injector){
            $compile = $injector.get('$compile');
            $rootScope = $injector.get('$rootScope');
            $httpBackend = $injector.get('$httpBackend');

            //Populate from stubs
            $rootScope.userDataURL = userDataURL;
            $rootScope.updateUsers = updateUsers;
            $rootScope.restore = restore;

            element = angular.element('\
                <div id="example"\
                    aero-list-typeahead\
                    label=""\
                    ng-model="user"\
                    async-attr="data"\
                    async-data="{{userDataURL}}"\
                    placeholder="&#128270; Name"\
                    title="Search users"\
                    parent-update="updateUsers(users, total, substring)"\
                    on-clear="restore()"\
                ></div>');
            $compile(element)($rootScope);
            $rootScope.$digest();

        }));

        beforeEach(function () {
            listUsersData = {
                data: [
                    {name: 'foo', email: 'test@test.com'},
                    {name: 'fop', email: 'test1@test.com'},
                    {name: '', email: 'test2@test.com'}],
                total: 3,
                pagination_limit: 20,
                use_restricted: true,
                me: {}
            };

            listUsersDataFiltered = {
                data: [
                    {name: 'foo', email: 'test@test.com'},
                    {name: 'fop', email: 'test1@test.com'}],
                total: 3,
                pagination_limit: 20,
                use_restricted: true,
                me: {}
            };

            $httpBackend
                .whenGET(userDataURL)
                .respond(listUsersData);

        });

        it('initializes', function () {
            expect(element.find('#example')).toBeDefined;
            expect(element.find('.typeahead')).toBeDefined;
            expect(element.find('.typeahead input')).toBeDefined;
            expect(element.find('.typeahead #typeahead-spinner')).toBeDefined;
        });

        it('accepts input and makes request', function () {
            var input = element.find('.typeahead input');
            var scope = element.isolateScope();

            expect(input[0].value).toBe('');
            expect(scope.selectedEntity.name).toBe('');
            expect(scope.matches.length).toBe(0);

            scope.selectedEntity.name = 'f';
            $httpBackend
                .expectGET(userDataURL + '?substring=f')
                .respond(listUsersData);
            $httpBackend.flush();

            expect(input[0].value).toBe('f');
            expect(scope.selectedEntity.name).toBe('f');
            expect(scope.matches.length).toBe(3);
        });

        it('calls the parent update on success', function () {
            var scope = element.isolateScope();
            spyOn(scope, 'parentUpdate').andCallThrough();

            scope.selectedEntity.name = 'b';
            $httpBackend
                .expectGET(userDataURL + '?substring=b')
                .respond(listUsersData);
            $httpBackend.flush();

            expect(scope.parentUpdate).toHaveBeenCalledWith({
                matches: listUsersData.data,
                total: listUsersData.total,
                substring: 'b'
            });

        });

        it('does not make request for cached value', function () {
            var input = element.find('.typeahead input');
            var scope = element.isolateScope();

            //Initial
            expect(input[0].value).toBe('');
            expect(scope.selectedEntity.name).toBe('');
            expect(scope.matches.length).toBe(0);

            //First Entry
            scope.selectedEntity.name = 'f';
            $httpBackend
                .expectGET(userDataURL + '?substring=f')
                .respond(listUsersData);
            $httpBackend.flush();

            expect(input[0].value).toBe('f');
            expect(scope.selectedEntity.name).toBe('f')
            expect(scope.matches.length).toBe(3);

            //Second Entry
            scope.selectedEntity.name = 'fo';
            $httpBackend
                .expectGET(userDataURL + '?substring=fo')
                .respond(listUsersDataFiltered);
            $httpBackend.flush();

            expect(input[0].value).toBe('fo');
            expect(scope.selectedEntity.name).toBe('fo')
            expect(scope.matches.length).toBe(2);

            //Delete a letter, this time we use the cache from before
            scope.selectedEntity.name = 'f';
            spyOn(scope, 'parentUpdate').andCallThrough();
            $rootScope.$digest();

            expect(input[0].value).toBe('f');
            expect(scope.selectedEntity.name).toBe('f')
            expect(scope.matches.length).toBe(3);
            expect(scope.parentUpdate).toHaveBeenCalledWith({
                matches: listUsersData.data,
                total: listUsersData.total,
                substring: 'f'
            });
        });

        it('handles the clearing of the value', function () {
            var input = element.find('.typeahead input');
            var scope = element.isolateScope();

            //Initial
            expect(input[0].value).toBe('');
            expect(scope.selectedEntity.name).toBe('');
            expect(scope.matches.length).toBe(0);

            //First Entry
            scope.selectedEntity.name = 'f';
            $httpBackend
                .expectGET(userDataURL + '?substring=f')
                .respond(listUsersData);
            $httpBackend.flush();

            //Clear
            spyOn(scope, 'onClear').andCallThrough();
            scope.selectedEntity.name = '';
            $rootScope.$digest();

            expect(input[0].value).toBe('');
            expect(scope.selectedEntity.name).toBe('');
            expect(scope.onClear).toHaveBeenCalled();
        });

    });
});