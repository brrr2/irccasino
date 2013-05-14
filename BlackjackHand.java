/*
	Copyright (C) 2013 Yizhe Shen <brrr@live.ca>
	
	This file is part of irccasino.

    irccasino is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    irccasino is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with irccasino.  If not, see <http://www.gnu.org/licenses/>.
*/

package irccasino;

import org.pircbotx.Colors;

public class BlackjackHand extends Hand {

	/**
	 * Class constructor for blackjack hand object
	 */
	public BlackjackHand(){
		super();
		
	}
	
	/* Formatted string repsentation of the hand */
	public String toString(int numHidden){
    	String hiddenBlock = Colors.DARK_BLUE+",00\uFFFD";
        String outStr= "";
        for (int ctr=0; ctr<numHidden; ctr++){
            outStr += hiddenBlock+" ";
        }
        for (int ctr=numHidden; ctr<getSize(); ctr++){
            outStr += cards.get(ctr)+" ";
        }
        return outStr.substring(0, outStr.length()-1)+Colors.NORMAL;
    }
}
