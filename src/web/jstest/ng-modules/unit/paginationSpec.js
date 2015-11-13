describe('Pagination', function () {

    beforeEach(module('pagination'));
    beforeEach(module('templates'));

    describe('aeroListPagination', function () {
        var $compile,
            $rootScope,
            $httpBackend,
            element;

        beforeEach(inject(function($injector){
            $compile = $injector.get('$compile');
            $rootScope = $injector.get('$rootScope');
            $httpBackend = $injector.get('$httpBackend');

            //Populate from stubs
            $rootScope.pages = pages;
            $rootScope.paginationInfo = paginationInfo;

            element = angular.element('\
                <div id="example"\
                    aero-pagination\
                    total="paginationInfo.total"\
                    offset="paginationInfo.offset"\
                    pagelimit="paginationInfo.limit"\
                    callback="paginationInfo.callback(offset, substring)">\
                </div>');
            $compile(element)($rootScope);
            $rootScope.$digest();

        }));

        it('initializes', function () {
            expect(element.find('#example')).toBeDefined;
            expect(element.find('.pagination-container')).toBeDefined;
            expect(element.find('ul.pagination')).toBeDefined;
            expect(element.find('ul.pagination li')).toBeDefined;
        });

        it('updates pages', function () {
            var input = element.find('ul.pagination');
            var scope = element.isolateScope();

            spyOn(scope, 'updatePages').andCallThrough();

            scope.total = 66;
            scope.updatePages();

            expect(scope.pages).toEqual([1,2,3,4]);
            expect(scope.currentPage).toEqual(1);

        });

        it('loads a new page', function () {
            var input = element.find('ul.pagination');
            var scope = element.isolateScope();

            spyOn(scope, 'callback').andCallThrough()
            spyOn($rootScope.paginationInfo, 'callback').andCallThrough();

            scope.currentPage = 3;
            scope.loadNewPage(scope.currentPage, 1);
            expect(scope.callback).toHaveBeenCalledWith({offset: 40});
            expect($rootScope.paginationInfo.callback).toHaveBeenCalledWith(40, undefined);
        });
    });
});