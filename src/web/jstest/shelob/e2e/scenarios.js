// N.B. these tests depend on some httpBackend mocking which is in appTest.js
// The dependency on this file is accomplished by navigating to index.html, which
// includes appTest.js

describe('Shelob App', function() {
    it('should show contents of root when first loaded', function() {
        browser().navigateTo('/index.html');
        expect(repeater('tbody tr.folder').count()).toBe(2);
        expect(repeater('tbody tr.file').count()).toBe(1);
    });

    it('should show contents of subfolder when navigated to #/:oid', function() {
        browser().navigateTo('/index.html#/9f89f89f8');
        expect(repeater('tbody tr.folder').count()).toBe(0);
        expect(repeater('tbody tr.file').count()).toBe(2);
    });

    it('should navigate to subfolder when subfolder link is clicked', function() {
        browser().navigateTo('/index.html');
        element('tbody tr.folder:eq(0) a').click();  // click the first link, which is empty_folder
        expect(browser().location().url()).toBe('/01a01a01a');
        expect(repeater('tbody tr.folder').count()).toBe(0);
        expect(repeater('tbody tr.file').count()).toBe(0);
    });
});
