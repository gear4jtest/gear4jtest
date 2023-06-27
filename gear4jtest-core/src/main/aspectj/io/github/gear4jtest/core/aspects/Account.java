package io.github.gear4jtest.core.aspects;

public class Account {
    int balance = 20;

    public boolean withdraw(int amount) {
        if (balance < amount) {
            return false;
        } 
        balance = balance - amount;
        return true;
    }
    
    public static void main(String[] args) {
        Account acc = new Account();
        acc.withdraw(5);

        Account acc2 = new Account();
        acc2.withdraw(25);
    }
}
