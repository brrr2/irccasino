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
