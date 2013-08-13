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

import java.util.ArrayList;
import org.pircbotx.Colors;

/**
 * An object that represents a hand of cards.
 * @author Yizhe Shen
 */
public class Hand {
    /** An ArrayList of Card representing the Hand. */
	protected ArrayList<Card> cards;
	
	/**
	 * Creates a Hand with an empty ArrayList of cards.
	 */
	public Hand(){
		cards = new ArrayList<Card>();
	}
	
	/* Accessor methods */
	/**
     * Adds the specified card to the hand.
     * @param card the card to add
     */
    public void add(Card card){
		cards.add(card);
	}
    
	/**
     * Adds the specified card to the hand at the specified index
     * @param card the card to add
     * @param index the index location to add the card
     */
    public void add(Card card, int index){
		cards.add(index, card);
	}
    
	/**
     * Adds the cardList to the end of this Hand.
     * @param cardList an ArrayList of cards
     */
    public void addAll(ArrayList<Card> cardList){
		if (cardList.size() > 0){
			cards.addAll(cardList);
		}
	}
    
	/**
     * Adds the cards from another hand to the end of this Hand.
     * @param hand the Hand to add
     */
    public void addAll(Hand hand){
		if (hand.getSize() > 0){
			cards.addAll(hand.getAllCards());
		}
	}
    
	/**
     * Removes the Card from this hand at the specified index.
     * @param index the index of the Card to remove
     */
    public void remove(int index){
		cards.remove(index);
	}
    
	/**
     * Removes the specified Card from this hand.
     * @param c the Card to remove
     */
    public void remove(Card c){
		cards.remove(c);
	}
    
	/**
     * Returns the Card at the specified index.
     * @param index the index of the Card
     * @return the desired Card
     */
    public Card get(int index){
		return cards.get(index);
	}
    
	/**
     * Returns the all Cards in the Hand in an ArrayList.
     * @return an ArrayList of Cards
     */
    public ArrayList<Card> getAllCards(){
		return cards;
	}
    
	/**
     * Empties the cards ArrayList.
     */
    public void clear(){
		cards.clear();
	}
    
	/**
     * Returns the number of Cards in this Hand.
     * @return the number of Cards
     */
    public int getSize(){
		return cards.size();
	}
    
	/**
     * Gets a sub-hand specified by the indices.
     * The sub-hand includes the starting index and excludes the end index.
     * 
     * @param start Starting index.
     * @param end End index.
     * @return A sub-hand of the hand
     */
    public Hand subHand(int start, int end){
		Hand h = new Hand();
		for (int ctr = start; ctr < end; ctr++){
			h.add(this.get(ctr));
		}
		return h;
	}
	
	/**
     * Default toString returns all cards in the hand face-up.
     * @return string representation of the cards in the hand all face-up
     */
    @Override
    public String toString(){
        return toString(0,getSize());
    }
    
	/**
     * Gets a string representation of the hand with hidden cards.
     * User can specify how many of the cards are hidden.
     * 
     * @param numHidden The number of hidden cards
     * @return a String with the first numHidden cards replaced
     */
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
    
    /**
     * Gets an index-select string representation of the hand.
     * A space-delimited string of cards starting from start and excluding end.
     * 
     * @param start the start index.
     * @param end the end index.
     * @return a String showing the selected cards
     */
    public String toString(int start, int end){
		String outStr= "";
		int slimit = Math.max(0, start);
        int elimit = Math.min(this.getSize(), end);
        for (int ctr=slimit; ctr<elimit; ctr++){
            outStr += cards.get(ctr)+" ";
        }
        return outStr.substring(0, outStr.length()-1)+Colors.NORMAL;
	}
}
