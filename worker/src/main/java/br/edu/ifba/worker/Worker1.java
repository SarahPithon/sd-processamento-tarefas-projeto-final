package br.edu.ifba.worker;

public class Worker1 extends Worker {
    
    public Worker1() {
        super("worker-1", "WORKER 1");
    }
    
    public static void main(String[] args) {
        Worker1 worker = new Worker1();
        worker.iniciar();
    }

}