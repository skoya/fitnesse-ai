package fitnesse.docstore;

public final class AttachmentRef {
  private final PageRef pageRef;
  private final String name;

  public AttachmentRef(PageRef pageRef, String name) {
    this.pageRef = pageRef;
    this.name = name;
  }

  public PageRef pageRef() {
    return pageRef;
  }

  public String name() {
    return name;
  }
}
