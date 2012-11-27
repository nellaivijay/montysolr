/**
 * 
 */
package org.apache.solr.analysis.author;

import java.io.IOException;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.util.BytesRef;

/**
 * @author jluker
 *
 */
public final class AuthorNormalizeFilter extends TokenFilter {

  private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
  private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);
  private final PayloadAttribute payloadAtt = addAttribute(PayloadAttribute.class);
  //private boolean setIntoPayload = true;

  /**
   * @param input
   */
  public AuthorNormalizeFilter(TokenStream input) {
    super(input);
  }

  /* (non-Javadoc)
   * @see org.apache.lucene.analysis.TokenStream#incrementToken()
   */
  @Override
  public boolean incrementToken() throws IOException {
    if (!input.incrementToken()) return false;
    
//    if (setIntoPayload ) {
//      payloadAtt.setPayload(new BytesRef(termAtt.toString()));
//    }
    
    String normalized = AuthorUtils.normalizeAuthor(termAtt.toString());
    termAtt.setEmpty().append(normalized);
    typeAtt.setType(AuthorUtils.AUTHOR_INPUT);
    
    return true;
  }
}