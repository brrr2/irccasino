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
import java.util.Random;

/**
 * A class for a deck of playing cards.
 * @author Yizhe Shen
 */
public class CardDeck {
    /** Card suits in unicode. */
    public static final String[] suits = {"\u2665","\u2666","\u2663","\u2660"};
    /** Card faces with T being 10. */
    public static final String[] faces = {"2","3","4","5","6","7","8","9","T","J","Q","K","A"};
    /** The main pile of cards. */
    private ArrayList<Card> cards;
    /** The discard pile. */
    private ArrayList<Card> discards;
    /** The number of sets of cards in this deck. */
    private int numDecks;
    
    /**
     * Default constructor creates a CardDeck with a single set of cards (52 cards).
     */
    public CardDeck(){
        this(1);
    }
    
    /**
     * Creates a CardDeck with n sets of cards (52n cards).
     * 
     * @param n number of sets of cards.
     */
    public CardDeck(int n){
        numDecks = n;
        cards = new ArrayList<Card>();
        discards = new ArrayList<Card>();
        makeCards();
    }
    
    /* Accessor methods */
    /**
     * Returns the number of sets of cards in this deck.
     * 
     * @return the number of sets of cards in this deck
     */
    public int getNumberDecks(){
        return numDecks;
    }
    
    /**
     * Returns the number of cards in the cards ArrayList.
     * 
     * @return the number of cards in the cards ArrayList
     */
    public int getNumberCards(){
        return cards.size();
    }
    
    /**
     * Returns the number of cards in the discards ArrayList.
     * 
     * @return the number of cards in the discards ArrayList.
     */
    public int getNumberDiscards(){
        return discards.size();
    }
    
    /**
     * Returns the cards ArrayList.
     * 
     * @return the cards
     */
    public ArrayList<Card> getCards(){
        return cards;
    }
    
    /**
     * Returns the discards ArrayList.
     * 
     * @return the discards
     */
    public ArrayList<Card> getDiscards(){
        return discards;
    }
    
    /* Card manipulation methods for the deck */
    /**
     * Takes the first Card from the cards ArrayList.
     * 
     * @return the card taken
     */
    public Card takeCard(){
        Card temp = cards.get(0);
        cards.remove(0);
        return temp;
    }
    
    /**
     * Adds the specified ArrayList<Card> to the discards.
     * 
     * @param cards the ArrayList<Card> to add to discards
     */
    public void addToDiscard(ArrayList<Card> cards){
        discards.addAll(cards);
    }
    
    /**
     * Adds the specified Card to the discards.
     * 
     * @param c the Card to discard
     */
    public void addToDiscard(Card c){
        discards.add(c);
    }
    
    /**
     * Merges the discards back into cards.
     */
    public void mergeDiscards(){
        if (discards.size() > 0){
            cards.addAll(discards);
            discards.clear();
        }
    }
    
    /**
     * Shuffles the cards in the deck.
     * A temporary ArrayList is created with the existing cards. The cards
     * ArrayList is then emptied. Cards are then randomly drawn one at a time 
     * from the temporary ArrayList and added back to the cards ArrayList in the 
     * order they are drawn. 
     */
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
    
    /**
     * Makes the deck full again.
     * Merges the discards back into the deck and then shuffles the deck.
     */
    public void refillDeck(){
        mergeDiscards();
        shuffleCards();
    }
    
    /**
     * Generates numDecks sets of cards.
     */
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