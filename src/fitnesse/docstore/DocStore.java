package fitnesse.docstore;

import java.io.InputStream;
import java.util.List;

public interface DocStore {
  PageRef resolve(String wikiPath);

  Page readPage(PageRef ref);

  void writePage(PageRef ref, PageWriteRequest req);

  List<PageRef> listChildren(PageRef ref);

  PageProperties readProperties(PageRef ref);

  void writeProperties(PageRef ref, PageProperties props);

  List<AttachmentRef> listAttachments(PageRef ref);

  InputStream readAttachment(AttachmentRef ref);

  void writeAttachment(AttachmentRef ref, InputStream data, Metadata meta);

  PageHistory history(PageRef ref, HistoryQuery q);
}
