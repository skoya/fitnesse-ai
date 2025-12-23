describe("TemplateInserter", function () {
    it("inserts template content and invokes callback", function () {
        var inserter = new TemplateInserter();
        var replaceSelection = jasmine.createSpy("replaceSelection");
        var codeMirrorDoc = { replaceSelection: replaceSelection };
        var callback = jasmine.createSpy("callback");

        spyOn($, "ajax").and.callFake(function (options) {
            options.success("template content");
        });

        inserter.insertInto(".TemplateLibrary.Example", codeMirrorDoc, callback);

        expect(replaceSelection).toHaveBeenCalledWith("template content");
        expect(callback).toHaveBeenCalled();
    });
});
