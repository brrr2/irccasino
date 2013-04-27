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