/*******************************************************************************
 *   Copyright (C) 2007, 2008, 2009, 2010, 2011, 2012 Peter Kolb
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

/***************************************************************************
 * This class provides a data structure that is used as return value for the
 * method DISCO.collocations(). 
 * @author peter
 ***************************************************************************/
public class ReturnDataCol {
    
    public String word;
    public float value;
    public int relation;

    ReturnDataCol() {
        word = "";
        value = 0.0F;
        relation = 0;
    }

    public ReturnDataCol(String w, float floatValue) {
        word = w;
        value = floatValue;
    }

    public ReturnDataCol(String w, float floatValue, int rel) {
        word = w;
        value = floatValue;
        relation = rel;
    }
}
