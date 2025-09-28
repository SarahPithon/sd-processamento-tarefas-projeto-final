package br.edu.ifba.worker;

public class Worker2 extends Worker {

    public Worker2() {
        super("worker-2", "WORKER 2");
    }

    public static void main(String[] args) {
        Worker2 worker = new Worker2();
        worker.iniciar();
    }
}