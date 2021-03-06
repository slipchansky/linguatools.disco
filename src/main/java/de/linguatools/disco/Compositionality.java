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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;

/**
 * This class contains support for compositional distributional semantics.
 * There are methods to compute the similarity between multi-word terms,
 * phrases and sentences or even paragraphs based on composition of the vectors
 * of individual words.
 * @author peter
 * @version 1.4
 */
public class Compositionality {
    
    /**
     * Implemented methods of vector composition.
     */
    public enum VectorCompositionMethod {
        /**
         * Simple vector addition.
         */
        ADDITION, 
        /**
         * Entry-wise multiplication.
         */
        MULTIPLICATION, 
        /**
         * Parameterized combination of addition and multiplication, see 
         * equation (11) in J. Mitchell and M. Lapata: 
         * <a href="http://aclweb.org/anthology-new/P/P08/P08-1028.pdf">Vector-based Models of
         * Semantic Composition</a>. Proceedings of ACL-08: HLT.<br/>
         * Compose vectors wv1 and wv2 by a combination of addition and 
         * multiplication:
         * <blockquote>p = alpha*wv1 + beta*wv2 + gamma*wv1*wv2</blockquote>
         * The contribution of multiplication and addition, as well
         * as the contribution of each of the two vectors can be controlled by the
         * three parameters alpha, beta and gamma.<br/>
         * For instance, in Mitchell and Lapata 2008 where wv1 is a verb and wv2 is
         * a noun, the parameters alpha, beta and gamma are set as follows:
         * <blockquote>alpha = 0.95<br/>
         * beta = 0<br/>
         * gamma = 0.05.</blockquote>
         * If one of alpha, beta, gamma is null, then these default values are used.
         */
        COMBINED,
        /**
         * Dilate word vector u along the direction of word vector v: 
         *  <blockquote>v' = u ° v<br/>
         *     = (u*u)v + (lambda-1)(u*v)u</blockquote>
         * where * is the dot product (Skalarprodukt).</br>
         * The default value (if lambda is null) for lambda is 2.0.<br/>
         * Contrary to the other composition methods, this operation is not
         * symmetric.<br/>
         * See chapter 4 of J. Mitchell: Composition in Distributional Models of
         * Semantics. PhD, Edinburgh, 2011.
         */
        DILATION;
    }
    
    /**
     * Available measures for vector comparison. 
     */
    public enum SimilarityMeasures { 
        /**
         * The well-known cosine similarity measure.
         */
        COSINE, 
        /**
         * The similarity measure described in the paper <a 
         * href="http://hdl.handle.net/10062/9731">Experiments on the difference
         * between semantic similarity and relatedness</a>. This is DISCO's
         * default measure of semantic similarity.
         */
        KOLB 
    }
    
    
    /***************************************************************************
     * Returns the collocations with their exact positions and their significance
     * values -- in other words the word vector representing the input word.
     * @param word input word (a single token, must not contain white space)
     * @return HashMap containing word vector or null
     * @throws IOException
     */
    private HashMap<String,Float> getWordvector(String word, DISCO disco)
            throws IOException{

        Document doc = disco.searchIndex(word);
        if ( doc == null ) return null;
        // die komprimiert gespeicherten Inhalte der Felder "Kol1" und "KolSig1"
        // bis "Kol6" und "KolSig6" holen
        HashMap<String,Float> result = new HashMap();
        String[] wordsBuffer;
        String[] valuesBuffer;
        for (int rel = 1; rel <= 6; rel++ ){
            wordsBuffer = doc.get("kol"+Integer.toString(rel)).split(" ");
            valuesBuffer = doc.get("kol"+Integer.toString(rel)+"Sig").split(" ");
            // BUG: TODO: im Index steht am Anfang der kol-Felder ein Leerzeichen!
            for(int i = 1; i < wordsBuffer.length; i++ ){
                // speichere Wort+Relation --> Wert
                result.put(wordsBuffer[i] + Integer.toString(rel), 
                        Float.parseFloat(valuesBuffer[i]));
            }
        }
        return result;
    }
    
    /**
     * Compute the dot product (inner product, scalar product) of wv1 and wv2.
     * @param wv1 first word vector
     * @param wv2 second word vector
     * @return result (a scalar, not a vector)
     */
    private float computeDotProduct(HashMap<String,Float> wv1, 
            HashMap<String,Float> wv2){
        
        float sp = 0.0F;
        
        for(Iterator it = wv1.keySet().iterator(); it.hasNext();){
            String w = (String) it.next();
            if( wv2.containsKey(w) ){
                sp = sp + wv1.get(w) * wv2.get(w);
            }
        }
        return sp;
    }
    
    /**
     * The following formula is used:
     * <blockquote>(wv1*wv1)wv2 + (lambda-1)(wv1*wv2)wv1</blockquote>
     * The default value (if lambda is null) for lambda is 2.0.<br/>
     * This composition method only works with the SimilarityMeasures.COSINE
     * similarity measure. 
     * @param wv1
     * @param wv2
     * @param lambda
     * @return 
     */
    private HashMap<String,Float> composeVectorsByDilation(
            HashMap<String,Float> wv1, HashMap<String,Float> wv2, Float lambda){
        
        if( lambda == null) lambda = 2.0F;
        
        float a = computeDotProduct(wv1, wv2);
        HashMap<String,Float> f1 = multiplicateWordVectorWithScalar(wv2, a);
        HashMap<String,Float> f2 = multiplicateWordVectorWithScalar(wv1, 
                a*(lambda-1));
        return composeVectorsByAddition(f1, f2);
    }
    
    /**
     * Multiply all values in the word vector hash with the scalar. 
     * @param wv word vector
     * @param scalar
     * @return 
     */
    private HashMap<String,Float> multiplicateWordVectorWithScalar(
            HashMap<String,Float> wv, float scalar){
        
        for(Iterator it = wv.keySet().iterator(); it.hasNext();){
            String w = (String) it.next();
            wv.put(w, wv.get(w) * scalar);
        }
        return wv;
    }
    
    /**
     * Compose vectors wv1 and wv2 by a combination of addition and 
     * multiplication:
     * <blockquote>p = alpha*wv1 + beta*wv2 + gamma*wv1*wv2</blockquote>
     * The contribution of multiplication and addition, as well
     * as the contribution of each of the two vectors can be controlled by the
     * three parameters alpha, beta and gamma.<br/>
     * For instance, in Mitchell and Lapata 2008 where wv1 is a verb and wv2 is
     * a noun, the parameters alpha, beta and gamma are set as follows:
     * <blockquote>alpha = 0.95<br/>
     * beta = 0<br/>
     * gamma = 0.05.</blockquote>
     * If one of alpha, beta, gamma is null, then these default values are used.
     * @param wv1 first word vector
     * @param wv2 second word vector
     * @param alpha weight of additive contribution of first word vector
     * @param beta weight of additive contribution of second word vector
     * @param gamma weight of multiplicative contribution of both word vectors
     * @return 
     */
    private HashMap<String,Float> composeVectorsByCombinedMultAdd(
            HashMap<String,Float> wv1, HashMap<String,Float> wv2, Float alpha, 
            Float beta, Float gamma){
        
        if( alpha == null || beta == null || gamma == null ){
            alpha = 0.95F;
            beta = 0.0F;
            gamma = 0.05F;
        }
        
        // Formula: result = a*wv1 + b*wv2 + c*wv1*wv2
        // m = wv1 * wv2
        HashMap<String,Float> m = composeVectorsByMultiplication(wv1, wv2);
        // m = c * m
        m = multiplicateWordVectorWithScalar(m, gamma);
        // k = a * wv1
        HashMap<String,Float> k = multiplicateWordVectorWithScalar(wv1, alpha);
        // l = b * wv2
        HashMap<String,Float> l = multiplicateWordVectorWithScalar(wv2, beta);
        // result = k + l + m
        return composeVectorsByAddition(composeVectorsByAddition(k,l),m);
    }
    
    /**
     * Combines two word vectors by multiplication.
     * @param wv1 word vector #1
     * @param wv2 word vector #2
     * @return the combined word vector
     */
    private HashMap<String,Float> composeVectorsByMultiplication(
            HashMap<String,Float> wv1, HashMap<String,Float> wv2){
        
        HashMap<String,Float> result = new HashMap();
        
        for( Iterator it = wv1.keySet().iterator(); it.hasNext(); ){
            String feature = (String) it.next();
            if( wv2.containsKey(feature) ){
                result.put(feature, wv1.get(feature) * wv2.get(feature));
            }
        }
        return result;
    }
    
    /**
     * Combines two word vectors by addition.
     * @param wv1
     * @param wv2
     * @return the combined word vector
     */
    private HashMap<String,Float> composeVectorsByAddition(
            HashMap<String,Float> wv1, HashMap<String,Float> wv2){
        
        HashMap<String,Float> result = new HashMap();
        
        // copy those features from wv1 to result that do not occur in wv2
        for( Iterator it = wv1.keySet().iterator(); it.hasNext(); ){
            String w = (String) it.next();
            if( !wv2.containsKey(w) ){
                result.put(w, wv1.get(w));
            }
        }
        
        // run through wv2 and add all common features of wv1 and wv2 to result,
        // and also those features from wv2 that do not occur in wv1
        for( Iterator it = wv2.keySet().iterator(); it.hasNext(); ){
            String w = (String) it.next();
            if( wv1.containsKey(w) ){
                result.put(w, wv1.get(w) + wv2.get(w));
            }else{
                result.put(w, wv2.get(w));
            }
        }
        
        return result;
    }
    
    /**
     * Compose two word vectors by the composition method given in 
     * <code>compositionMethod</code>.
     * @param wv1 word vector #1
     * @param wv2 word vector #2
     * @param compositionMethod One of the methods in 
     * <code>VectorCompositionMethod</code>.
     * @return the resulting word vector or <code>null</code>.
     */
    public HashMap<String,Float> composeWordVectors(HashMap<String,Float> wv1,
            HashMap<String,Float> wv2, VectorCompositionMethod compositionMethod,
            Float alpha, Float beta, Float gamma, Float lambda){
    
        if( compositionMethod == VectorCompositionMethod.ADDITION ){
            return composeVectorsByAddition(wv1, wv2);
        }else if( compositionMethod == VectorCompositionMethod.MULTIPLICATION ){
            return composeVectorsByMultiplication(wv1, wv2);
        }else if( compositionMethod == VectorCompositionMethod.COMBINED ){
            return composeVectorsByCombinedMultAdd(wv1, wv2, alpha, beta, gamma); 
        }else if( compositionMethod == VectorCompositionMethod.DILATION ){
            return composeVectorsByDilation(wv1, wv2, lambda);     
        }else{
            return null;
        }
    }
    
    /**
     * Compose two or more word vectors by the composition method given in 
     * <code>compositionMethod</code>.
     * @param wordvectorList a list of word vectors to be combined. The list has
     * to have at least two elements. The ordering of the list has no influence
     * on the result.
     * @param compositionMethod One of the methods in 
     * <code>VectorCompositionMethod</code>.
     * @return the resulting word vector or <code>null</code>.
     */
    public HashMap<String,Float> composeWordVectors(ArrayList<HashMap<String,Float>>
            wordvectorList, VectorCompositionMethod compositionMethod, Float alpha, 
            Float beta, Float gamma, Float lambda){
        
        if( wordvectorList.size() < 2 ) return null;
        
        // combine the first two vectors in the list
        HashMap<String,Float> wv = composeWordVectors(wordvectorList.get(0),
                wordvectorList.get(1), compositionMethod, alpha, beta, gamma, lambda);
        
        for(int i = 2; i < wordvectorList.size(); i++){
            wv = composeWordVectors(wv, wordvectorList.get(i), compositionMethod,
                    alpha, beta, gamma, lambda);
        }
        return wv;
    }
    
    /**
     * Utility function. Prints the word vector to standard output.
     * @param wordvector 
     */
    public void printWordVector(HashMap<String,Float> wordvector){
        
        for(Iterator it = wordvector.keySet().iterator(); it.hasNext();){
            String w = (String) it.next();
            System.out.println(w+"\t"+wordvector.get(w));
        }
    }
    
    /**
     * This method compares two word vectors using the similarity measure
     * SimilarityMeasures.KOLB that is described in the paper
     * <blockquote>Peter Kolb. <a href="http://hdl.handle.net/10062/9731">Experiments
     * on the difference between semantic similarity and relatedness</a>. In 
     * <i>Proceedings of the <a href="http://beta.visl.sdu.dk/nodalida2009/">17th
     * Nordic Conference on Computational Linguistics - NODALIDA '09</a></i>, 
     * Odense, Denmark, May 2009.</blockquote>
     * @param wv1 a word vector
     * @param wv2 another word vector
     * @return the similarity between the two word vectors; a value between 0.0F
     * and 1.0F.
     */
    private float computeSimilarityKolb(HashMap<String,Float> wv1, 
            HashMap<String,Float> wv2){
        
        float nenner = 0;
        for( Iterator it = wv1.keySet().iterator(); it.hasNext(); ){
            nenner += wv1.get( (String) it.next());
        }
        
        float zaehler = 0;
        for( Iterator it = wv2.keySet().iterator(); it.hasNext(); ){
            String w = (String) it.next();
            float v = wv2.get(w);
            if ( wv1.containsKey(w) ){
                zaehler += (v + wv1.get(w));
            }
            nenner += v;
        }
        return 2 * zaehler / nenner;  // DICE-KOEFFIZIENT !
    }
    
    /**
     * This method compares two word vectors using the similarity measure
     * SimilarityMeasures.COSINE.
     * @param wv1 a word vector
     * @param wv2 another word vector
     * @return the similarity between the two word vectors; a value between -1.0F
     * and 1.0F. A return value of -2.0F indicates an error.
     */
    private float computeSimilarityCosine(HashMap<String,Float> wv1, 
            HashMap<String,Float> wv2){
        
        float nenner1 = 0.0F;
        for( Iterator it = wv1.keySet().iterator(); it.hasNext(); ){
            float v = wv1.get( (String) it.next());
            nenner1 += v * v;
        }
        
        float nenner2 = 0, zaehler = 0;
        for( Iterator it = wv2.keySet().iterator(); it.hasNext(); ){
            String w = (String) it.next();
            float v = wv2.get(w);
            if ( wv1.containsKey(w) ){
                zaehler += (v * wv1.get(w));
            }
            nenner2 += v * v;
        }
        return (float) (zaehler / Math.sqrt(nenner1 * nenner2));
    }
    
    /**
     * Computes the semantic similarity (according to the vector similarity 
     * measure <code>similarityMeasure</code>) between the two input word 
     * vectors.<br/>
     * @param wordvector1
     * @param wordvector2
     * @param similarityMeasure One of the similarity measures enumerated in
     * <code>DISCO.SimilarityMeasures</code>.
     * @return The similarity between the two input word vectors; depending on
     * the chosen similarity measure a value between 0.0F and 1.0F, or -1.0F and 
     * 1.0F. In case the <code>similarityMeasure</code> is unknown the return
     * value is -3.0F.
     */
    public float semanticSimilarity(HashMap<String,Float> wordvector1, 
            HashMap<String,Float> wordvector2, SimilarityMeasures simMeasure){
        
        if( simMeasure == SimilarityMeasures.KOLB ){
            return computeSimilarityKolb(wordvector1, wordvector2);
        }else if( simMeasure == SimilarityMeasures.COSINE ){
            return computeSimilarityCosine(wordvector1, wordvector2);
        }else{
            return -3.0F;
        }
    }
    
    /***************************************************************************
     * Computes the semantic similarity (according to the vector similarity 
     * measure <code>SimilarityMeasures.KOLB</code> which is described in 
     * <a href="http://hdl.handle.net/10062/9731">Kolb 2009</a>) between the 
     * two input word vectors.
     * @param wordvector1
     * @param wordvector2
     * @return The similarity between the two input word vectors; a value
     * between 0.0F and 1.0F.
     */
    public float semanticSimilarity(HashMap<String,Float> wordvector1, 
            HashMap<String,Float> wordvector2){
        
        return computeSimilarityKolb(wordvector1, wordvector2);
    }
    
    /**
     * This method computes the semantic similarity between two multi-word terms,
     * phrases, sentences or paragraphs. This is done by composition of the word
     * vectors of the constituent words.<br/>
     * Each of the two input strings is split at whitespace, and the wordvectors
     * of the individual tokens (constituent words) are retrieved. Then the
     * word vectors are combined using the method <code>composeWordVectors()</code>.
     * The two resulting vectors are then compared with 
     * <code>Compositionality.semanticSimilarity()</code>.
     * @param multiWords1 a tokenized string containing a multi-word term, phrase,
     * sentence or paragraph.
     * @param multiWords2 a tokenized string containing a multi-word term, phrase,
     * sentence or paragraph.
     * @param compositionMethod
     * @param simMeasure
     * @param disco a DISCO word space
     * @param alpha The parameters <code>alpha</code>, <code>beta</code> and 
     * <code>gamma</code> are used in the composition method 
     * <code>VectorCompositionMethod.COMBINED</code> (see there for more 
     * information). If one of the values is
     * <code>null</code> default values are used.
     * @param beta See description for parameter <code>alpha</code>!
     * @param gamma See description for parameter <code>alpha</code>!
     * @param lambda Used by <code>VectorCompositionMethod.DILATION</code> (see
     * there for more information). If
     * <code>lambda</code> is <code>null</code>, a default value is used.
     * @return the distributional similarity between <code>multiWord1</code> and
     * <code>multiWord2</code>.
     * @see de.linguatools.disco.Compositionality.VectorCompositionMethod
     * @see de.linguatools.disco.Compositionality.SimilarityMeasures
     */
    public float compositionalSemanticSimilarity(String multiWords1, 
            String multiWords2, VectorCompositionMethod compositionMethod, 
            SimilarityMeasures simMeasure, DISCO disco, Float alpha, 
            Float beta, Float gamma, Float lambda) throws IOException{
        
        multiWords1 = multiWords1.trim();
        multiWords2 = multiWords2.trim();
        String[] multi1 = multiWords1.split("\\s+");
        String[] multi2 = multiWords2.split("\\s+");
        
        // compute word vector #1
        HashMap<String,Float> wv1 = new HashMap();
        if( multi1.length == 1 ){
            wv1 = getWordvector(multi1[0], disco);
        }else if( multi1.length == 2 ){
            wv1 = composeWordVectors(getWordvector(multi1[0], disco),
                getWordvector(multi1[1], disco), compositionMethod, alpha, beta,
                gamma, lambda);
        }else{
            wv1 = composeWordVectors(getWordvector(multi1[0], disco),
                getWordvector(multi1[1], disco), compositionMethod, alpha, beta,
                gamma, lambda);
            for(int i = 2; i < multi1.length; i++){
                wv1 = composeWordVectors(wv1, getWordvector(multi1[i], disco),
                        compositionMethod, alpha, beta, gamma, lambda);
            }
        }
        
        // compute word vector 21
        HashMap<String,Float> wv2 = new HashMap();
        if( multi2.length == 1 ){
            wv2 = getWordvector(multi2[0], disco);
        }else if( multi2.length == 2 ){
            wv2 = composeWordVectors(getWordvector(multi2[0], disco),
                getWordvector(multi2[1], disco), compositionMethod, alpha, beta,
                gamma, lambda);
        }else{
            wv2 = composeWordVectors(getWordvector(multi2[0], disco),
                getWordvector(multi2[1], disco), compositionMethod, alpha, beta,
                gamma, lambda);
            for(int i = 2; i < multi2.length; i++){
                wv2 = composeWordVectors(wv2, getWordvector(multi2[i], disco),
                        compositionMethod, alpha, beta, gamma, lambda);
            }
        }
        
        // compute similarity between the two word vectors
        return semanticSimilarity(wv1, wv2, simMeasure);
    }
    
    /**
     * Find the most similar words in the DISCO word space for an input word 
     * vector. While the word vector can represent a multi-token word (if it was
     * produced by one of the methods 
     * <code>Compositionality.composeWordVectors()</code>) the most
     * similar words will only be single-token words from the index.<br/>
     * <b>Warning</b>: This method is very time consuming and should only be
     * used with word spaces that have been loaded into memory!
     * @param wordvector input word vector
     * @param disco DISCO word space
     * @param simMeasure
     * @return List of all words (with their similarity values) whose similarity
     * with the <code>wordvector</code> is greater than zero, ordered by 
     * similarity value (highest value first).
     * @throws IOException 
     */
    public ArrayList<ReturnDataCol> similarWords(HashMap<String,Float> wordvector,
            DISCO disco, SimilarityMeasures simMeasure)
            throws IOException{
        
        // hole einen IndexReader fuer das indexDir
        IndexReader ir = disco.getIndexReader();
        
        // durchlaufe alle Dokumente
        ArrayList<ReturnDataCol> result = new ArrayList();
        for(int i = 0; i < ir.numDocs(); i++){
            Document doc = null;
            try {
                doc = ir.document(i);
            } catch (CorruptIndexException ex) {
                continue;
            } catch (IOException ex) {
                continue;
            }
            // Wortvektor zu Wort Nr. i holen
            String word = doc.get("word");
            HashMap<String,Float> wv = getWordvector(word, disco);
            // Ähnlichkeit zwischen Wortvektoren berechnen
            float sim = semanticSimilarity(wordvector, wv, simMeasure);
            if( sim > 0.0F){
                ReturnDataCol r = new ReturnDataCol(word, sim);
                result.add(r);
            }
        }
        
        // nach höchstem Ähnlichkeitswert sortieren
        Collections.sort(result, new ValueComparator());
        
        return result;
    }
}
