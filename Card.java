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

public class Card implements Comparable<Card>{
    private String suit, face;
    
    /**
     * Creates a new Card with suit and face.
     * @param s Card suit.
     * @param f Card face.
     */
    public Card(String s, String f){
        suit = s;
        face = f;
    }
    
    /* Accessor methods */
    public String getFace(){
        return face;
    }
    public String getSuit(){
        return suit;
    }
    public int getBlackjackValue() {
		int num;
        if (face.equals("A")){
            num = 11; // Give aces a default value of 11
        } else {
            try {
                num = Integer.parseInt(getFace());
            } catch (NumberFormatException e) {
                return 10;
            }
        }
		return num;
	}
    public int getFaceValue(){
    	for (int ctr=0; ctr < CardDeck.faces.length; ctr++){
    		if (face.equals(CardDeck.faces[ctr])){
    			return ctr;
    		}
    	}
    	return -1;
    }
    public int getSuitValue(){
    	for (int ctr=0; ctr < CardDeck.suits.length; ctr++){
    		if (suit.equals(CardDeck.suits[ctr])){
    			return ctr;
    		}
    	}
    	return -1;
    }
    
    /* Compares using face value, then suit */
    @Override
    public int compareTo(Card c){
    	int valueDiff = getFaceValue() - c.getFaceValue();
    	int suitDiff = getSuitValue() - c.getSuitValue();
    	if (valueDiff == 0){
    		return suitDiff;
    	} else {
    		return valueDiff;
    	}
    }
    
    /* String representation of the card with IRC color formatting */
    @Override
    public String toString(){
        String color;
        if (suit.equals(CardDeck.suits[0])){
            color = Colors.RED;
        } else if ( suit.equals(CardDeck.suits[1])){
            color = Colors.BROWN;
        } else if ( suit.equals(CardDeck.suits[2])){
            color = Colors.DARK_BLUE;
        } else {
        	color = Colors.BLACK;
        }
        
        return color+",00"+face+suit;
    }
}