package org.apache.lucene.analysis.synonym;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.text.ParseException;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.synonym.SynonymFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.analysis.util.*;
import org.apache.lucene.util.Version;

/**
 * Factory for {@link SynonymFilter}.
 * <pre class="prettyprint" >
 * &lt;fieldType name="text_synonym" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.SynonymFilterFactory" synonyms="synonyms.txt" 
 *             format="solr" ignoreCase="false" expand="true" 
 *             tokenizerFactory="solr.WhitespaceTokenizerFactory"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 * 
 * If the LUCENE-4499 gets committed, we can remove these NewSynonym... classes.
 */
public class NewSynonymFilterFactory extends TokenFilterFactory implements ResourceLoaderAware {
  private SynonymMap map;
  private boolean ignoreCase;
  
  @Override
  public TokenStream create(TokenStream input) {
    // if the fst is null, it means there's actually no synonyms... just return the original stream
    // as there is nothing to do here.
    return map.fst == null ? input : new SynonymFilter(input, map, ignoreCase);
  }
  
  //@Override
  public void inform(ResourceLoader loader) throws IOException {
    final boolean ignoreCase = getBoolean("ignoreCase", false); 
    this.ignoreCase = ignoreCase;

    String bf = args.get("builderFactory");
    SynonymBuilderFactory builder = loadBuilderFactory(loader, bf != null ? bf : SynonymBuilderFactory.class.getName());
    
    try {
      map = builder.create(loader);
    } catch (ParseException e) {
      throw new IOException(e);
    }
  }
  
  
  public static class SynonymParser extends SynonymMap.Builder {

    public SynonymParser(boolean dedup) {
      super(dedup);
    }

    public void add(Reader in) throws IOException, ParseException {
      throw new IllegalAccessError("You must override this method");
    }
  }
  
  
  public static class SynonymBuilderFactory extends AbstractAnalysisFactory implements ResourceLoaderAware {
    
    public SynonymMap create(ResourceLoader loader) throws IOException, ParseException {
      
      String synonyms = args.get("synonyms");
      if (synonyms == null)
        throw new IllegalArgumentException("Missing required argument 'synonyms'.");
      
      CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
      
      SynonymParser parser = getParser(getAnalyzer(loader));
      
      File synonymFile = new File(synonyms);
      if (synonymFile.exists()) {
        decoder.reset();
        parser.add(new InputStreamReader(loader.openResource(synonyms), decoder));
      } else {
        List<String> files = splitFileNames(synonyms);
        for (String file : files) {
          decoder.reset();
          parser.add(new InputStreamReader(loader.openResource(file), decoder));
        }
      }
      return parser.build();
      
    }
    
    protected Analyzer getAnalyzer(ResourceLoader loader) throws IOException {
      final boolean ignoreCase = getBoolean("ignoreCase", false); 

      String tf = args.get("tokenizerFactory");

      final TokenizerFactory factory = tf == null ? null : loadTokenizerFactory(loader, tf);
      
      return new Analyzer() {
        @Override
        protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
          Tokenizer tokenizer = factory == null ? new WhitespaceTokenizer(Version.LUCENE_50, reader) : factory.create(reader);
          TokenStream stream = ignoreCase ? new LowerCaseFilter(Version.LUCENE_50, tokenizer) : tokenizer;
          return new TokenStreamComponents(tokenizer, stream);
        }
      };
    }
    
    protected SynonymParser getParser(Analyzer analyzer) {
      
      String format = args.get("format");
      boolean expand = getBoolean("expand", true);
      
      if (format == null || format.equals("solr")) {
        // TODO: expose dedup as a parameter?
        return new NewSolrSynonymParser(true, expand, analyzer);
      } else if (format.equals("wordnet")) {
        return new NewWordnetSynonymParser(true, expand, analyzer);
      } else {
        // TODO: somehow make this more pluggable
        throw new IllegalArgumentException("Unrecognized synonyms format: " + format);
      }
    }
    
    
    // (there are no tests for this functionality)
    private TokenizerFactory loadTokenizerFactory(ResourceLoader loader, String cname) throws IOException {
      TokenizerFactory tokFactory = loader.newInstance(cname, TokenizerFactory.class);
      tokFactory.setLuceneMatchVersion(luceneMatchVersion);
      tokFactory.init(args);
      if (tokFactory instanceof ResourceLoaderAware) {
        ((ResourceLoaderAware) tokFactory).inform(loader);
      }
      return tokFactory;
    }

    public void inform(ResourceLoader loader) throws IOException {
      // do nothing
    }
  }
  
  private SynonymBuilderFactory loadBuilderFactory(ResourceLoader loader, String cname) throws IOException {
    SynonymBuilderFactory builderFactory = loader.newInstance(cname, SynonymBuilderFactory.class);
    builderFactory.setLuceneMatchVersion(luceneMatchVersion);
    builderFactory.init(args);
    if (builderFactory instanceof ResourceLoaderAware) {
      ((ResourceLoaderAware) builderFactory).inform(loader);
    }
    return builderFactory;
  }
}
