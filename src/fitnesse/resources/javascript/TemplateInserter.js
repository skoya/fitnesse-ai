function TemplateInserter()
{
  this.insertInto = function(templateValue, codeMirrorDoc, onInserted) {
    
    if(templateValue !== "")
    {
      var pageDataUrl = templateValue.substr(1, templateValue.length - 1) + "?pageData";
      
      $.ajax({
        url: pageDataUrl,
        success: function(result) {
          codeMirrorDoc.replaceSelection(result);
          if (typeof onInserted === "function") {
            onInserted();
          }
        },
        error: function() {
          alert("Error Accessing Template");
        }
      });
    }
  };
}
