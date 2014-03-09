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

import java.util.Comparator;

/********************************************************************
 * This class provides a comparator used for sorting objects of type
 * ReturnDataCol.
 * @author peter
 ********************************************************************/
public class ValueComparator implements Comparator{

    /*********************************************************************
     * Compare two ReturnDataCol objects according to their value.
     * @param o1 the first object
     * @param o2 the second object
     * @return 1 if o1 < o2; 0 if o1 == o2; -1 if o1 > o2
     * @throws ClassCastException if the two objects are not ReturnDataCol
     */
    @Override
    public int compare(Object o1, Object o2){
	int retval = 0;
	if ( o1 instanceof ReturnDataCol && o2 instanceof ReturnDataCol ){
	    ReturnDataCol c1 = (ReturnDataCol) o1;
	    ReturnDataCol c2 = (ReturnDataCol) o2;
	    if( c1.value < c2.value ) retval = 1;
	    if( c1.value > c2.value ) retval = -1;
	} else {
	    throw new ClassCastException("ValueComparator: Illegal arguments!");
	}
	return(retval);
    }

}// end of class
