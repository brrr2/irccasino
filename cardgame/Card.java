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

package irccasino.cardgame;

import org.pircbotx.Colors;

/**
 * An object that represents a card. 
 * @author Yizhe Shen
 */
public class Card implements Comparable<Card>{
    /** The card's suit. */
    private String suit;
    /** The card's face. */
    private String face;
    
    /**
     * Creates a new Card with suit and face.
     * 
     * @param s Card suit.
     * @param f Card face.
     */
    public Card(String s, String f){
        suit = s;
        face = f;
    }
    
    /* Accessor methods */
    /**
     * Returns the card's face.
     * 
     * @return the card's face
     */
    public String getFace(){
        return face;
    }
    
    /**
     * Returns the card's suit.
     * 
     * @return the card's suit
     */
    public String getSuit(){
        return suit;
    }
    
    /**
     * Returns the blackjack value of this card.
     * 
     * @return 10 for face card, 11 for ace or the parsed Integer of the face
     */
    public int getBlackjackValue() {
        int num;
        if (face.equals("A")){
            num = 11; // Give aces a default value of 11
        } else {
            try {
                num = Integer.parseInt(face);
            } catch (NumberFormatException e) {
                num = 10;
            }
        }
        return num;
    }
    
    /**
     * Returns the index in the static array CardDeck.faces that matches this
     * card's face.
     * 
     * @return the index or -1 if not found
     */
    public int getFaceValue(){
        for (int ctr=0; ctr < CardDeck.faces.length; ctr++){
            if (face.equals(CardDeck.faces[ctr])){
                return ctr;
            }
        }
        return -1;
    }
    
    /**
     * Returns the index in the static array CardDeck.suits that matches this
     * card's suit.
     * 
     * @return the index or -1 if not found
     */
    public int getSuitValue(){
        for (int ctr=0; ctr < CardDeck.suits.length; ctr++){
            if (suit.equals(CardDeck.suits[ctr])){
                return ctr;
            }
        }
        return -1;
    }
    
    /**
     * Determines if this card is of the same suit as another card.
     * @param c the other card
     * @return true if they have the same suit
     */
    public boolean sameSuit(Card c) {
        return isSuit(c.suit);
    }
    
    /**
     * Determines if this card has the same face as another card.
     * @param c the other card
     * @return true if they have the same face
     */
    public boolean sameFace(Card c) {
        return isFace(c.face);
    }
    
    /**
     * Determines if this card is of the specified suit.
     * @param tsuit the specified suit
     * @return true if this card of that suit
     */
    public boolean isSuit(String tsuit) {
        return suit.equals(tsuit);
    }
    
    /**
     * Determines if this card has the specified face.
     * @param tface the specified face
     * @return true if this card has that face
     */
    public boolean isFace(String tface) {
        return face.equals(tface);
    }
    
    /** 
     * Compares this Card to another by face value, then by suit value. 
     * 
     * @param c the Card to compare
     * @return -1 if face value is less or if equal, suit value is less, 0 if
     * suit value and face value are equal, 1 if face value is greater or if 
     * equal, suit value is greater
     */
    @Override
    public int compareTo(Card c){
        if (c == null) {
            throw new NullPointerException();
        }
        int valueDiff = getFaceValue() - c.getFaceValue();
        int suitDiff = getSuitValue() - c.getSuitValue();
        if (valueDiff == 0){
            return suitDiff;
        } else {
            return valueDiff;
        }
    }
    
    /**
     * String representation of the card with IRC color formatting.
     * Gives the card a colour based on suit and adds a white background.
     * 
     * @return a IRC colour formatted string with the card's face and suit
     */
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
    
    /**
     * Checks equality based on suit and face.
     * @param o the other Card
     * @return true if the other Card has the same suit and face
     */
    @Override
    public boolean equals(Object o) {
        if (o != null && o instanceof Card) {
            Card c = (Card) o;
            if (face.equals(c.face) && suit.equals(c.suit) &&
                hashCode() == c.hashCode()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hash code based on suit and face.
     * @return the hash
     */
    @Override
    public int hashCode() {
        int hash = 5;
        hash = 83 * hash + suit.hashCode();
        hash = 83 * hash + face.hashCode();
        return hash;
    }
}