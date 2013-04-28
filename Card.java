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

import org.pircbotx.*;

public class Card {
    private String suit, face;
    
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
    
    /* String representation of the card with IRC color formatting */
    @Override
    public String toString(){
        String color;
        if (suit.equals(CardDeck.suits[0]) || suit.equals(CardDeck.suits[1])){
            color = Colors.RED;
        } else {
            color = Colors.BLACK;
        }
        
        return color+",00"+face+suit+Colors.NORMAL;
    }
}