/*******************************************************************************
 *   Copyright (C) 2007, 2008, 2009, 2010, 2011, 2012, 2013 Peter Kolb
 *   peter.kolb@linguatools.org
 *
 *   Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *   use this file except in compliance with the License. You may obtain a copy
 *   of the License at 
 *   
 *        http://www.apache.org/licenses/LICENSE-2.0 
 *
 *   Unless required by applicable law or agreed to in writing, software 
 *   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the 
 *   License for the specific language governing permissions and limitations
 *   under the License.
 *
 ******************************************************************************/

package de.linguatools.disco;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.queryparser.classic.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Query;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;

/*******************************************************************************
 * DISCO (Extracting DIStributionally Similar Words Using CO-occurrences) provides
 * a number of methods for computing the distributional (i.e. semantic) similarity
 * between arbitrary words, for retrieving a word's collocations or its corpus
 * frequency. It also provides a method to retrieve the semantically most similar
 * words for a given word.
 * @author peter
 * @version 1.4
 *******************************************************************************/
public class DISCO {

    // private data fields
    private String indexName = null;
    private RAMDirectory indexRAM = null;
    private IndexSearcher is = null;
    private Analyzer analyzer = null;
    private QueryParser parser = null;
    private Version version = Version.LUCENE_CURRENT;
    
    public  DISCO(String idxName) throws IOException {
    	this (idxName, false);
    }
    
    /***************************************************************************
     * A complete word space can be loaded into RAM to
     * speed up similarity computations. Make sure that you have enough free
     * memory since word spaces can be very large. Also, remember that loading a
     * huge word space into RAM will take some time.
     * @param idxName the word space directory
     * @param loadIntoRAM if true the word space is loaded into RAM
     * @throws IOException
     */
    public DISCO(String idxName, boolean loadIntoRAM) throws IOException{
    	
    	QueryParserBase b;
        
        indexName = idxName;
        analyzer = new WhitespaceAnalyzer(version);
        parser = new QueryParser(version, "word", analyzer);
        parser.setDefaultOperator(QueryParser.Operator.AND);
        if(loadIntoRAM == true){
            indexRAM = new RAMDirectory(FSDirectory.open(new File(indexName)), IOContext.READ);
            is = new IndexSearcher(IndexReader.open(indexRAM));
        }else{
            is = new IndexSearcher(IndexReader.open(
                    FSDirectory.open(new File(indexName))));
        }
    }

    /***************************************************************************
     * Searches for a word in index field "word" and returns the first hit
     * Document or null.<br/>
     * DISCO uses the <a href="http://lucene.apache.org">Lucene</a> index. A
     * word's data are stored in the index in an object of type Document. A 
     * Document has the following 16 fields:<ul>
     * <li>"word": contains a word, tokenized with WhitespaceAnalyzer. This is
     * the only searchable field.</li>
     * <li>"freq": the corpus frequency of the word. This field is only stored,
     * but not indexed.</li>
     * <li>"dsb": the distributionally similar words for word. They are stored
     * in a single string, in which the words are separated by spaces. This
     * field is not indexed, and therefore not searchable.</li>
     * <li>"dsbSim": contains a single string with the similarity values for the
     * words in the field "dsb", separated by spaces. The string in this field 
     * is parallel to the string in the field "dsb", i.e., the n-th value of the
     * string in "dsbSim" corresponds to the n-th word in "dsb".<br/>
     * <b>To get correct values precede each value string with "0." before 
     * converting it to float!</b> (the leading "0." of all values have been
     * deleted in order to reduce index size).<br/>
     * Example: field "dsb" contains the string "apple banana cherry", field 
     * "dsbSim" contains the string "3241 1233 0788". This means that the
     * similarity between the word in the field "word" and cherry is 0.0788.
     * </li>
     * <li>"kol1": contains all words that have occurred three words left to
     * "word" in the corpus (collocations). Stored as space-separated single
     * string.</li>
     * <li>"kol1Sig": contains the significance values for "kol1", in a string
     * parallel to the string in "kol1".</li>
     * <li>"kol2": same as "kol1" but two words to the left.</li>
     * <li>"kol2Sig": same as "kol1Sig".</li>
     * <li>...</li>
     * <li>"kol6": same as "kol1" but three words to the right.</li>
     * <li>"kol6Sig" </li>
     * </ul>
     * @param word word to be looked up in index
     * @return index entry of word or null
     */
    public Document searchIndex(String word)
            throws IOException{
        try {
            // Anfrage tokenisieren und parsen
            Query query;
            query = parser.parse(word); // can throw ParseException !
            // nach Anfrage im Index suchen
            TopDocs hits = is.search(query, 1);
            if( hits.totalHits == 0 ) return null;
            // Nur den ersten Treffer verwenden (es sollte nur einen geben)
            Document doc = is.doc(hits.scoreDocs[0].doc);
            return doc;
        } catch (ParseException ex) {
            System.err.println("Error: ParseException: "+ex);
            return null;
        }
    }

    /***************************************************************************
     * returns the number of Documents (i.e. words) in the index.
     * @return number of words in index
     * @throws java.io.IOException
     */
    public int numberOfWords() throws IOException{
        // erzeuge einen IndexReader fuer das indexDir
        IndexReader ir = IndexReader.open(FSDirectory.open(new File(indexName)));
        // Hole Anzahl Dokumente im Index
        return ( ir.numDocs() );
    }

    /***************************************************************************
     * Looks up the input word in the index and returns its frequency.
     * If the word is not found the return value is zero.
     * @param word word to be looked up (a single token, must not contain white 
     * space)
     * @return frequency of the input word (0 if word is not found)
     * @throws java.io.IOException
     */
    public int frequency(String word) throws IOException{
        Document doc = searchIndex(word);
        if ( doc == null ) return 0;
        return Integer.parseInt( doc.get("freq") );
    }

    /***************************************************************************
     * Looks up the input word in the index and returns its distributionally
     * similar words ordered by decreasing similarity together with similarity
     * values. If the search word isn't found in the index, the return value is
     * null.<br/>
     * <a href="http://www.cs.ualberta.ca/~lindek/papers/acl98.pdf">Lin</a>'s
     * similarity measure was used to compute the similar words.
     * @param word word to be looked up (a single token, must not contain white 
     * space)
     * @return result data structure or null
     * @throws IOException
     */
    public ReturnDataBN similarWords(String word)
            throws IOException{
        Document doc = searchIndex(word);
        if ( doc == null ) return null;
        // die komprimiert gespeicherten Inhalte der Felder "dsb" und "dsbSim"
        // holen
        ReturnDataBN res = new ReturnDataBN();
        res.words = doc.get("dsb").split(" ");
        res.values = doc.get("dsbSim").split(" ");
        return res;
    }

    /***************************************************************************
     * Returns the collocations for the input word together with their
     * significance values, ordered by significance value (highest significance
     * first).<br/>
     * <b>Important note:</b> The collocations are summed up over their different
     * positions, and the variable <code>relation</code> in the returned data
     * structure is not set.<br/>
     * If the search word isn't found in the index, the return value is
     * null.<br/>
     * The significance values were computed using <a
     * href="http://www.cs.ualberta.ca/~lindek/papers/acl98.pdf">Lin</a>'s
     * measure.
     * @param word the input word (a single token, must not contain white space)
     * @return the list of collocations with their significance values
     * @see de.linguatools.disco.DISCO#wordvector(java.lang.String)
     * @throws java.io.IOException
     */
    public ReturnDataCol[] collocations(String word)
            throws IOException{

        Document doc = searchIndex(word);
        if ( doc == null ) return null;
        // die komprimiert gespeicherten Inhalte der Felder "Kol1" und "KolSig1"
        // bis "Kol6" und "KolSig6" holen und in ein Hash speichern. Die
        // einzelnen Relationen (1-6) werden zusammengefasst und die jeweiligen
        // Signifikanzwerte addiert.
        HashMap colloHash = new HashMap();
        String[] wordsBuffer;
        String[] valuesBuffer;
        int i; int rel;
        float sig;
        for ( rel = 1; rel <= 6; rel++ ){
            wordsBuffer = doc.get("kol"+Integer.toString(rel)).split(" ");
            valuesBuffer = doc.get("kol"+Integer.toString(rel)+"Sig").split(" ");
            for( i = 1; i < wordsBuffer.length; i++ ){
                if( colloHash.get(wordsBuffer[i]) == null ){
                    colloHash.put(wordsBuffer[i],
                            Float.parseFloat(valuesBuffer[i]) );
                }else{
                    sig = (float) (Float.parseFloat(valuesBuffer[i])) +
                            ((Float) colloHash.get(wordsBuffer[i]));
                    colloHash.put(wordsBuffer[i], sig);
                }
            }
        }
        // jetzt das Hash in ein Array speichern und nach Signifikanz sortieren
        ReturnDataCol[] res = new ReturnDataCol[colloHash.size()];
        i = 0;
        for( Iterator it = colloHash.keySet().iterator(); it.hasNext(); ){
            String w = (String) it.next();
            res[i++] = new ReturnDataCol(w,
                    ((Float)colloHash.get(w)).floatValue() );
        }
        // sortiere Array ReturnDataCol[] nach hoechstem Signifikanzwert
        Arrays.sort( res, new ValueComparator() );
        return res;
    }

    /***************************************************************************
     * Returns the collocations with their exact positions and their significance
     * values -- in other words the word vector representing the input word.
     * @param word input word (a single token, must not contain white space)
     * @return data structure containing word vector or null
     * @throws IOException
     */
    public ReturnDataCol[] wordvector(String word)
            throws IOException{

        Document doc = searchIndex(word);
        if ( doc == null ) return null;
        // die komprimiert gespeicherten Inhalte der Felder "Kol1" und "KolSig1"
        // bis "Kol6" und "KolSig6" holen
        ArrayList<ReturnDataCol> buffer = new ArrayList<ReturnDataCol>();
        String[] wordsBuffer;
        String[] valuesBuffer;
        for (int rel = 1; rel <= 6; rel++ ){
            wordsBuffer = doc.get("kol"+Integer.toString(rel)).split(" ");
            valuesBuffer = doc.get("kol"+Integer.toString(rel)+"Sig").split(" ");
            // BUG: TODO: im Index steht am Anfang der kol-Felder ein Leerzeichen!
            for(int i = 1; i < wordsBuffer.length; i++ ){
                ReturnDataCol rdc = new ReturnDataCol(wordsBuffer[i],
                        Float.parseFloat(valuesBuffer[i]), rel);
                buffer.add(rdc);
            }
        }
        ReturnDataCol[] res = new ReturnDataCol[buffer.size()];
        for(int i = 0; i < buffer.size(); i++){
            res[i] = buffer.get(i);
        }
        // sortiere Array ReturnDataCol[] nach hoechstem Signifikanzwert
        Arrays.sort( res, new ValueComparator() );
        return res;
    }

    /***************************************************************************
     * Computes the first order similarity (according to <a
     * href="http://www.cs.ualberta.ca/~lindek/papers/acl98.pdf">Lin</a>'s
     * vector similarity measure) between the input words based on their
     * collocation sets. If any of the two words isn't found in the index, the
     * return value is -1.
     * @param w1 input word #1 (a single token, must not contain white space)
     * @param w2 input word #2 (a single token, must not contain white space)
     * @return similarity value (between 0 and 1 or -1 if word not found)
     * @throws java.io.IOException
     */
    public float firstOrderSimilarity(String w1, String w2)
            throws IOException{
        // die beiden zu vergleichenden Wörter im Index nachschlagen
        Document doc1 = searchIndex(w1);
        Document doc2 = searchIndex(w2);
        if ( doc1 == null || doc2 == null ) return -1;
        // Kollokationen von Wort #1 durchlaufen (über alle Relationen), in Hash
        // speichern (nach Relationen unterschieden) und alle Werte addieren.
        HashMap colloHash = new HashMap();
        String[] wordsBuffer;
        String[] valuesBuffer;
        int i; int rel;
        float nenner = 0.0F, v;
        for ( rel = 1; rel <= 6; rel++ ){
            wordsBuffer = doc1.get("kol"+Integer.toString(rel)).split(" ");
            valuesBuffer = doc1.get("kol"+Integer.toString(rel)+"Sig").split(" ");
            for( i = 1; i < wordsBuffer.length; i++ ){
                v = Float.parseFloat(valuesBuffer[i]);
                colloHash.put(wordsBuffer[i]+"_"+Integer.toString(rel), v );
                nenner += v;
            }
        }
        // Kollokationen von Wort #2 durchlaufen (über alle Relationen), mit den
        // Kollokationen von Wort #1 im Hash vergleichen und ggf. die Werte zum
        // Zähler addieren und alle Werte zum Nenner addieren.
        float zaehler = 0.0F;
        for ( rel = 1; rel <= 6; rel++ ){
            wordsBuffer = doc2.get("kol"+Integer.toString(rel)).split(" ");
            valuesBuffer = doc2.get("kol"+Integer.toString(rel)+"Sig").split(" ");
            for( i = 1; i < wordsBuffer.length; i++ ){
                v = Float.parseFloat(valuesBuffer[i]);
                if ( colloHash.containsKey(wordsBuffer[i]+"_"+Integer.toString(rel)) ){
                    zaehler += v + (Float)
                            colloHash.get(wordsBuffer[i]+"_"+Integer.toString(rel));
                }
                nenner += v;
            }
        }
        float erg = zaehler / nenner;
        // catch rounding errors
        if( erg > 1.0F){
            return 1.0F;
        }else{
            return erg;
        }
    }

    /***************************************************************************
     * Computes the second order similarity (according to Lin's measure) between
     * the input words based on the sets of their distributional similar words.
     * If any of the two words isn't found in the index, the return value is -1.
     * @param w1 input word #1 (a single token, must not contain white space)
     * @param w2 input word #2 (a single token, must not contain white space)
     * @return similarity value
     * @throws java.io.IOException
     */
    public float secondOrderSimilarity(String w1, String w2)
            throws IOException{
        // die beiden zu vergleichenden Wörter im Index nachschlagen
        Document doc1 = searchIndex(w1);
        Document doc2 = searchIndex(w2);
        if ( doc1 == null || doc2 == null ) return -1;
        // ähnliche Wörter von Wort #1 durchlaufen, in Hash speichern und alle
        // Werte addieren.
        HashMap simHash = new HashMap();
        String[] wordsBuffer;
        String[] valuesBuffer;
        int i;
        float nenner = 0, v;
        wordsBuffer = doc1.get("dsb").split(" ");
        valuesBuffer = doc1.get("dsbSim").split(" ");
        for( i = 1; i < wordsBuffer.length; i++ ){
            // aus Speicherplatzgründen wurde beim Indexieren die führende "0."
            // weggelassen -- wieder anfügen:
            v = Float.parseFloat("0."+valuesBuffer[i]);
            simHash.put(wordsBuffer[i], v );
            nenner += v;
        }
        // ähnliche Wörter von Wort #2 durchlaufen, mit den ähnlichen Wörtern von
        // Wort #1 im Hash vergleichen und ggf. die Werte zum Zähler addieren und
        // alle Werte zum Nenner addieren.
        float zaehler = 0;
        wordsBuffer = doc2.get("dsb").split(" ");
        valuesBuffer = doc2.get("dsbSim").split(" ");
        for( i = 1; i < wordsBuffer.length; i++ ){
            // beim Indexieren weggelassene "0." wieder anfügen
            v = Float.parseFloat("0."+valuesBuffer[i]);
            if ( simHash.containsKey(wordsBuffer[i]) ){
                zaehler += v + (Float) simHash.get(wordsBuffer[i]);
            }
            nenner += v;
        }
        return zaehler / nenner;
    }
    
    /**
     * Return the common context of <code>w1</code> and <code>w2</code>. The 
     * common context is computed by retrieving the wordvectors for both words
     * and comparing them. "Common" means that the same word must occur in the
     * same relative position with the target word.
     * @param w1 input word #1 (a single token, must not contain white space)
     * @param w2 input word #2 (a single token, must not contain white space)
     * @return A list with features (word + relation) that occur in the 
     * wordvectors of both input words. The list is ordered by the sum of the 
     * significance values of both input words, highest significance first.<br/> 
     * If one or both of <code>w1</code> or <code>w2</code> are not found in the
     * index <code>null</code> is returned. If both words are found in the index
     * but they have no common context, then an empty list is returned. 
     * @throws IOException 
     */
    public ArrayList<ReturnDataCommonContext> commonContext(String w1, String w2)
            throws IOException{
    
        // die Kollokationen (= Kontext) zum ersten Wort holen und mit 
        // ihren Werten in ein Hash speichern
        ReturnDataCol[] res;
        HashMap<String,Float> w1ColloHash = new HashMap();
        res = wordvector(w1);
        if ( res == null ) return null;
        for(int k = 0; k < res.length; k++){
            // speichere w1+Relation --> Wert
            w1ColloHash.put(res[k].word+Integer.toString(res[k].relation),
                    res[k].value);
        }
        // die Kollokationen zum zweiten Wort holen und mit denen im Hash
        // vergleichen. Gleiche Wörter in ein neues Hash speichern.
        ArrayList<ReturnDataCommonContext> result = new ArrayList();
        ReturnDataCol[] res2 = wordvector(w2);
        if ( res2 == null ) return null;
        for(int k = 0; k < res2.length; k++){
            if ( w1ColloHash.containsKey(res2[k].word+Integer.toString(
                    res2[k].relation)) ){
                result.add(new ReturnDataCommonContext(res2[k].word, 
                        w1ColloHash.get(res2[k].word+Integer.toString(res2[k].relation)),
                        res2[k].value, res2[k].relation));
            }
        }
        // sortiere ArrayList nach hoechstem Signifikanzwert
        Collections.sort( result, new CommonContextComparator() );
        return result;
    }

    /***************************************************************************
     * This method closes the RAMDirectory where the word space is stored and
     * sets all internal variables of the DISCO instance to <code>null</code>.
     * The sole purpose of this method is to release the memory that is
     * associated with a word space loaded into RAM. <b>Subsequent calls to the
     * DISCO instance will throw NullPointerExceptions!</b> In most cases it is
     * not necessary for a program to call this method. Normally, you do not
     * have to destroy a DISCO instance after using it.
     */
    public void destroy(){
        if( indexRAM != null ){
            indexRAM.close();
            indexRAM = null;
        }
        is = null;
        indexName = null;
    }

    /***************************************************************************
     * Run trough all documents (i.e. queryable words) in the index, and retrieve
     * the word and its frequency. Write both informations to the file named
     * outputFileName. This method can be used to check index integrity.<br/>
     * @param outputFileName
     * @return number of words written to the output file. In case of success the
     * value is equal to the number of words in the index.
     */
    public int wordFrequencyList(String outputFileName){
                
       // erzeuge einen IndexReader fuer das indexDir
        IndexReader ir = null;
        try {
            if( indexRAM != null ){
                ir = IndexReader.open(indexRAM);
            }else{
                ir = IndexReader.open(FSDirectory.open(new File(indexName)));
            }
        } catch (CorruptIndexException ex) {
            System.out.println(DISCO.class.getName()+": "+ex);
            return -1;
        } catch (IOException ex) {
            System.out.println(DISCO.class.getName()+": "+ex);
            return -1;
        }

        // Hole Anzahl Dokumente im Index
        int N = ir.numDocs();
        
        // öffne Ausgabedatei
        FileWriter fw;
        try {
            fw = new FileWriter(outputFileName);
        } catch (IOException ex) {
            System.out.println(DISCO.class.getName()+": "+ex);
            return -1;
        }
        
        // durchlaufe alle Dokumente
        int corrupt = 0;
        int ioerror = 0;
        int i = 0;
        for(i = 0; i < N; i++){
            Document doc = null;
            try {
                doc = ir.document(i);
            } catch (CorruptIndexException ex) {
                corrupt++;
                continue;
            } catch (IOException ex) {
                ioerror++;
                continue;
            }
            // Wort Nr. i holen
            String word = doc.get("word");
            // Frequenz von Wort i holen
            int f = Integer.parseInt(doc.get("freq"));
            try {
                // Wort und Frequenz in Ausgabe schreiben
                fw.write(word+"\t"+f+"\n");
            } catch (IOException ex) {
                System.out.println(DISCO.class.getName()+": word "+i+": "+ex);
                return i;
            }
            // Info ausgeben
            if( i % 100 == 0 ){
                System.out.print("\r"+i);
            }
        }
        System.out.println();
        if( corrupt > 0 || ioerror > 0 ){
            int e = corrupt + ioerror;
            System.out.println("*** WARNING! ***");
            System.out.println("The language data packet \""+indexName+"\" "
                    + "has "+e+" defect entries ("+corrupt+" corrupt, "+ioerror+
                    " IO errors)");
            System.out.println("All functioning words have been written to "+
                    outputFileName);
        }
        
        // aufräumen
        try {
            fw.close();
            ir.close();
        } catch (IOException ex) {
            System.out.println(DISCO.class.getName()+": "+ex);
            return -1;
        }
        
        return (i - corrupt - ioerror);
    }
    
    /**
     * Return an IndexReader for the current DISCO index. You should close the
     * IndexReader if it is not used anymore.
     * @return IndexReader for the current DISCO index.
     */
    public IndexReader getIndexReader(){
        
        // erzeuge einen IndexReader fuer das indexDir im RAM
        IndexReader ir = null;
        try {
            if( indexRAM != null ){
                ir = IndexReader.open(indexRAM);
            }else{
                ir = IndexReader.open(FSDirectory.open(new File(indexName)));
            }
        } catch (CorruptIndexException ex) {
            System.out.println(DISCO.class.getName()+": "+ex);
            return null;
        } catch (IOException ex) {
            System.out.println(DISCO.class.getName()+": "+ex);
            return null;
        }
        return ir;
    }
    
    /**
     * Data structure used by method <code>DISCO.commonContext</code>.
     */
    public class ReturnDataCommonContext{
        
        /**
         * Context word.
         */
        public String word;
        /**
         * Significance value between word and w1.
         */
        public float valueW1;
        /**
         * Significance value between word and w2.
         */
        public float valueW2;
        /**
         * Relative position of the word in the context. "Common" means that 
         * both word and position are identical.
         */
        public int relation;
        
        /**
         * Constructor.
         * @param word
         * @param valueW1
         * @param valueW2
         * @param relation 
         */
        public ReturnDataCommonContext(String word, float valueW1, float valueW2,
                int relation) {
            
            this.word = word;
            this.valueW1 = valueW1;
            this.valueW2 = valueW2;
            this.relation = relation;
        }
    }
    
    /**
     * Comparator for class ReturnDataCommonContext.
     */
    public class CommonContextComparator implements Comparator{

        /*********************************************************************
         * Compare two ReturnDataCommonContext objects according to the sum of
         * their values (valueW1 + valueW2).
         * @param o1 the first object
         * @param o2 the second object
         * @return 1 if o1 < o2; 0 if o1 == o2; -1 if o1 > o2
         * @throws ClassCastException if the two objects are not ReturnDataCol
         */
        @Override
        public int compare(Object o1, Object o2){
            int retval = 0;
            if ( o1 instanceof ReturnDataCommonContext && o2 instanceof 
                    ReturnDataCommonContext ){
                ReturnDataCommonContext c1 = (ReturnDataCommonContext) o1;
                ReturnDataCommonContext c2 = (ReturnDataCommonContext) o2;
                if( (c1.valueW1 + c1.valueW2) < (c2.valueW1 + c2.valueW2 ) )
                    retval = 1;
                if( (c1.valueW1 + c1.valueW2) > (c2.valueW1 + c2.valueW2) )
                    retval = -1;
            } else {
                throw new ClassCastException("CommonContextComparator:"
                        + " Illegal arguments!");
            }
            return(retval);
        }
    }
    
}
