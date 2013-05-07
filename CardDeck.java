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

import java.util.*;

public class CardDeck {
    protected static final String[] suits = {"\u2665","\u2666","\u2663","\u2660"};
    protected static final String[] faces = {"2","3","4","5","6","7","8","9","T","J","Q","K","A"};
    protected ArrayList<Card> cards;
    protected ArrayList<Card> discards;
    protected int numDecks;
    
    /**
     * Default constructor creates a CardDeck with a single set of cards (52 cards).
     */
    public CardDeck(){
        this(1);
    }
    /**
     * Creates a CardDeck with n sets of cards (52n cards).
     * 
     * @param n		number of sets of cards.
     */
    public CardDeck(int n){
        numDecks = n;
        cards = new ArrayList<Card>();
        discards = new ArrayList<Card>();
        makeCards();
    }
    
    /* Accessor methods */
    public int getNumberDecks(){
    	return numDecks;
    }
    public int getNumberCards(){
        return cards.size();
    }
    public int getNumberDiscards(){
        return discards.size();
    }
    public ArrayList<Card> getCards(){
    	return cards;
    }
    public ArrayList<Card> getDiscards(){
    	return discards;
    }
    
    /* Card manipulation methods for the deck */
    public Card takeCard(){
    	Card temp = cards.get(0);
        cards.remove(0);
        return temp;
    }
    public void addToDiscard(ArrayList<Card> c){
        discards.addAll(c);
    }
    public void mergeDiscards(){
        if (discards.size() > 0){
            cards.addAll(discards);
            discards.clear();
        }
    }
    public void shuffleCards(){
        ArrayList<Card> tCards = new ArrayList<Card>(cards);
        int randomNum;
        Random numGen = new Random();        
        cards.clear();
        while(!tCards.isEmpty()){
            randomNum = numGen.nextInt(tCards.size());
            cards.add(tCards.get(randomNum));
            tCards.remove(randomNum);
        }
    }
    public void refillDeck(){
    	mergeDiscards();
    	shuffleCards();
    }
    
    /* Method to generate numDecks sets of cards */
    private void makeCards(){
    	for (int n=0; n<numDecks; n++){
	        for (int ctr=0; ctr<suits.length; ctr++){
	            for (int ctr2=0; ctr2<faces.length; ctr2++){
	                cards.add(new Card(suits[ctr],faces[ctr2]));
	            }
	        }
    	}
    }
}