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

public class StatFileLine{
    private String nick;
    private int stack;
    private int bankrupts;
    private int bank;
    private int bjrounds;
    private int tprounds;
    private boolean simple;
    
    public StatFileLine(){
        this("",0,0,0,0,0,true);
    }
    
    public StatFileLine(String nick, int stack, int bank, int bankrupts, 
            int bjrounds, int tprounds, boolean simple){
        this.nick = nick;
        this.stack = stack;
        this.bankrupts = bankrupts;
        this.bank = bank;
        this.bjrounds = bjrounds;
        this.tprounds = tprounds;
        this.simple = simple;
    }
    
    public String getNick(){
        return nick;
    }
    
    public void setNick(String value){
        nick = value;
    }
    
    public int getStack(){
        return stack;
    }
    
    public void setStack(int value){
        stack = value;
    }
    
    public int getBankrupts(){
        return bankrupts;
    }
    
    public void setBankrupts(int value){
        bankrupts = value;
    }
    
    public int getBank(){
        return bank;
    }
    
    public void setBank(int value){
        bank = value;
    }
    
    public int getBJRounds(){
        return bjrounds;
    }
    
    public void setBJRounds(int value){
        bjrounds = value;
    }
    
    public int getTPRounds(){
        return tprounds;
    }
    
    public void setTPRounds(int value){
        tprounds = value;
    }
    
    public boolean getSimple(){
        return simple;
    }
    
    public void setSimple(boolean value){
        simple = value;
    }
    
    @Override
    public String toString(){
        return nick + " " + stack + " " + bank + " " + bankrupts + " " +
                bjrounds + " " + tprounds + " " + simple;
    }
}