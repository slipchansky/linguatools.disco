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

import de.linguatools.disco.DISCO.ReturnDataCommonContext;
import java.io.*;
import java.io.File;
import java.util.ArrayList;

/*******************************************************************************
 * This class provides the command line interface for DISCO.
 * @author peter
 *******************************************************************************/
public class Main{
    
    
    /*******************************************************************
     * Print usage information.
     *******************************************************************/
    private static void printUsage(){
        System.out.println("DISCO V1.4 -- www.linguatools.de/disco/");
        System.out.println("(C) 2007-2013 Peter Kolb");
        System.out.println("Usage: java -jar disco-1.4.jar <indexDir> <option>");
        System.out.println("Options:\t-f <w>\t\treturn corpus frequency of word " +
                "<w>");
        System.out.println("\t\t-s <w1> <w2>\treturn first order similarity " +
                "between words <w1> and <w2>");
        System.out.println("\t\t-s2 <w1> <w2>\treturn second order similarity " +
                "between words <w1> and <w2>");
        System.out.println("\t\t-bn <w> <n>\treturn the <n> most similar words " +
                "for word <w>");
        System.out.println("\t\t-bs <w> <s>\treturn all words that are at least" +
                " <s> similar to word <w>");
        System.out.println("\t\t-bc <w> <n>\treturn the <n> best collocations" +
                " for word <w>");
        System.out.println("\t\t-cc <w1> <w2>\treturn the common context for" +
                " <w1> and <w2>");
        System.out.println("\t\t-n\t\treturn the number of words in the index");
        System.out.println("\t\t-wl <file>\twrite word frequency list to file");
    }    
    
    /********************************************************************************
     * Main method. Invoke from command line. For options type "java -jar
     * disco-1.4.jar".
     * For more information consult the documentation or visit DISCO's 
     * <a href="http://www.linguatools.de/disco/disco_en.html">web site</a>.
     * @param args command line options
     *********************************************************************************/
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            printUsage();
        }else{
            // erstes Argument muss Index-Verzeichnis sein
            File indexDir = new File(args[0]);
            if( ! indexDir.isDirectory() ){
                System.out.println("Error: can't open directory "+args[0]);
                printUsage();
                return;
            }
            
            // jetzt kommt einer der acht Befehle -f -s -s2 -bn -bs -bc -cc -n
            
            //////////////////////////////////////////            
            // -f <w>: return frequency of word <w> //
            //////////////////////////////////////////
            if( args[1].equals("-f") ){
                if ( args[2] == null ){
                    printUsage();
                    return;
                }
                try {
                    DISCO d = new DISCO(args[0]);
                    int freq = d.frequency(args[2]);
                    System.out.println(freq);
                } catch (IOException ex) {
                    System.out.println("Error: IOException: "+ex);
                }
            }
            /////////////////////////////////////////////////////////////////            
            // -s <w1> <w2>: return similarity between words <w1> and <w2> //
            /////////////////////////////////////////////////////////////////
            else if( args[1].equals("-s") ){
                if ( args[2] == null || args[3] == null ){
                    printUsage();
                    return;
                }
                try {
                    DISCO d = new DISCO(args[0]);
                    float sim = d.firstOrderSimilarity(args[2], args[3]);
                    if ( sim == -1 ){
                        System.out.println("Error: Word not found in index.");
                    }else{
                        System.out.println(sim);
                    }
                } catch (IOException ex) {
                    System.out.println("Error: IOException: "+ex);
                }  
            }
            ///////////////////////////////////////////////////////////////////////////////            
            // -s2 <w1> <w2>: return second order similarity between words <w1> and <w2> //
            ///////////////////////////////////////////////////////////////////////////////
            else if( args[1].equals("-s2") ){
                if ( args[2] == null || args[3] == null ){
                    printUsage();
                    return;
                }
                try {
                    DISCO d = new DISCO(args[0]);
                    float sim = d.secondOrderSimilarity(args[2], args[3]);
                    if ( sim == -1 ){
                        System.out.println("Error: Word not found in index.");
                    }else{
                        System.out.println(sim);
                    }
                } catch (IOException ex) {
                    System.out.println("Error: IOException: "+ex);
                }
            }
            /////////////////////////////////////////////////////////////////            
            // -bn <w> <n>: return the <n> most similar words for word <w> //
            /////////////////////////////////////////////////////////////////
            else if( args[1].equals("-bn") ){
                if ( args[2] == null || args[3] == null ){
                    printUsage();
                    return;
                }
                ReturnDataBN res = new ReturnDataBN();
                try {
                    DISCO d = new DISCO(args[0]);
                    res = d.similarWords(args[2]);
                    if ( res == null ){ 
                        System.out.println("The word \""+args[2]+"\" was not found."); 
                        return; 
                    }
                    int n = Integer.parseInt(args[3]) - 1;
                    for(int k = 1; k < res.words.length; k++){   // BUG im Indexer für V1
                        System.out.println(res.words[k]+"\t0."+res.values[k]);
                        if( k > n ) break;   // k >= n
                    }
                } catch (IOException ex) {
                    System.out.println("Error: IOException: "+ex);
                }
            }
            ////////////////////////////////////////////////////////////////////////            
            // -bs <w> <s>: return all words that are at least <s> similar to <w> //
            ////////////////////////////////////////////////////////////////////////
            else if( args[1].equals("-bs") ){
                if ( args[2] == null || args[3] == null ){
                    printUsage();
                    return;
                }
                ReturnDataBN res = new ReturnDataBN();
                try {
                    DISCO d = new DISCO(args[0]);
                    res = d.similarWords(args[2]);
                    if ( res == null ){ 
                        System.out.println("The word \""+args[2]+"\" was not found."); 
                        return; 
                    }
                    float s = Float.parseFloat(args[3]);
                    for(int k = 1; k < res.words.length; k++){
                        if( Float.parseFloat("0."+res.values[k]) < s ) break;
                        System.out.println(res.words[k]+"\t0."+res.values[k]);
                    }
                } catch (IOException ex) {
                    System.out.println("Error: IOException: "+ex);
                }
            }
            ////////////////////////////////////////////////////////////////            
            // -bc <w> <n>: return the <n> best collocations for word <w> //
            ////////////////////////////////////////////////////////////////
            else if( args[1].equals("-bc") ){
                if ( args[2] == null || args[3] == null ){
                    printUsage();
                    return;
                }
                ReturnDataCol[] res;
                try {
                    DISCO d = new DISCO(args[0]);
                    res = d.collocations(args[2]);
                    if ( res == null ){ 
                        System.out.println("The word \""+args[2]+"\" was not found."); 
                        return; 
                    }
                    int n = Integer.parseInt(args[3]) - 1;
                    for(int k = 0; k < res.length; k++){
                        System.out.println(res[k].word+"\t"+res[k].value);
                        if( k >= n ) break;
                    }
                } catch (IOException ex) {
                    System.out.println("Error: IOException: "+ex);
                }
            }
            ////////////////////////////////////////////////////////////////            
            // -cc <w1> <w2>: return the common context for <w1> and <w2> //
            ////////////////////////////////////////////////////////////////
            else if( args[1].equals("-cc") ){
                if ( args[2] == null || args[3] == null ){
                    printUsage();
                    return;
                }
                DISCO d = new DISCO(args[0]);
                ArrayList<ReturnDataCommonContext> res = d.commonContext(args[2], 
                        args[3]);
                if( res == null ){
                    System.out.println("One of the input words was not found.");
                    return;
                }
                if( res.isEmpty() ){
                    System.out.println("No common context.");
                    return;
                }
                for(int k = 0; k < res.size(); k++){
                    System.out.println(res.get(k).word+"\t"+res.get(k).relation+
                            "\t"+res.get(k).valueW1+"\t"+res.get(k).valueW2);
                }
                return;
            }
            /////////////////////////////////////////////////
            // -n: return the number of words in the index //
            /////////////////////////////////////////////////
            else if( args[1].equals("-n") ){
                try {
                    DISCO d = new DISCO(args[0]);
                    int n = d.numberOfWords();
                    System.out.println(n);
                } catch (IOException ex) {
                    System.out.println("Error: IOException: "+ex);
                }    
            }
            ////////////////////////////////////////////
            // -wl: write word frequency list to file //
            ////////////////////////////////////////////
            else if( args[1].equals("-wl") ){
                try {
                    DISCO d = new DISCO(args[0]);
                    int i = d.wordFrequencyList(args[2]);
                    System.out.println(i+" of "+d.numberOfWords()+" words were written.");
                } catch (IOException ex) {
                    System.out.println("Error: IOException: "+ex);
                }  
                
            // unbekannte Option
            }else{
                System.out.println("Error: unknown command line option: "+args[1]);
                printUsage();
                return;
            }
        }
    }

}// end of class
