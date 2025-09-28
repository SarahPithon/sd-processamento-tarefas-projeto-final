package br.edu.ifba.worker;

public class Worker3 extends Worker {
    
    public Worker3() {
        super("worker-3", "WORKER 3");
    }
    
    public static void main(String[] args) {
        Worker3 worker = new Worker3();
        worker.iniciar();
    }
}