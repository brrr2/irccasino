package irccasino;

import org.pircbotx.*;

public class BlackjackPlayer extends Player{
	protected boolean surrender, hit;
	protected int insureBet;
	
	/* Constructor requires User and player type */
    public BlackjackPlayer(User u, boolean d){
        super(u,d);
        surrender = false;
        hit = false;
        insureBet = 0;
    }
    
    /* Blackjack specific methods */
    public void setSurrender(boolean b){
        surrender = b;
    }
    public boolean hasSurrendered(){
        return surrender;
    }
    public void setHit(boolean b){
        hit = b;
    }
    public boolean hasHit(){
        return hit;
    }
    public boolean hasInsured(){
    	return (insureBet > 0);
    }
    public void setInsureBet(int amount){
    	insureBet = amount;
    	cash -= amount;
    }
    public int getInsureBet(){
    	return insureBet;
    }
    public void clearInsureBet(){
    	insureBet = 0;
    }
}
