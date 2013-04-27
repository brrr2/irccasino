package irccasino;

import java.util.*;

public class CardDeck {
    protected static final String[] suits = {"\u2665","\u2666","\u2663","\u2660"};
    protected static final String[] faces = {"2","3","4","5","6","7","8","9","T","J","Q","K","A"};
    protected ArrayList<Card> cards;
    protected ArrayList<Card> discards;
    protected int numDecks;
    
    /* Default constructor creates single set */
    public CardDeck(){
        this(1);
    }
    /* Creates a CardDeck with n sets */
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
    
    /* Method to generate n sets of cards */
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