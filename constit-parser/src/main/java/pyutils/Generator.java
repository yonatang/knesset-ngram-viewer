package pyutils;

import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public abstract class Generator<T> implements Iterable<T> {

	final protected BlockingQueue<T> queue;
	private boolean hasIter = false;
	private boolean done = false;

	public Generator() {
		this(1);
	}

	public Generator(int amount) {
		this.queue = new ArrayBlockingQueue<T>(amount);
	}

	protected abstract void func();

	protected void yield(T item) {
		try {
			this.queue.put(item);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private synchronized void done() {
		this.done = true;
	}

	private synchronized boolean isDone() {
		return this.done;
	}

	public synchronized Iterator<T> iterator() {
		if (this.hasIter) {
			throw new RuntimeException("Only one iterator per generator!");
		}
		this.hasIter = true;
		Runnable r = new Runnable() {
			public void run() {
				Generator.this.func();
				Generator.this.done();
			}
		};
		Thread t = new Thread(r);
		t.start();
		return new InternalIterator<T>(this.queue);
	}

	private class InternalIterator<T> implements Iterator<T> {

		private BlockingQueue<T> _q;

		private InternalIterator(BlockingQueue<T> q) {
			this._q = q;
		}

		public boolean hasNext() {
			return !(Generator.this.isDone() && this._q.isEmpty());
		}

		public T next() {
			try {
				return this._q.take();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
		}

		public void remove() {
			this._q.remove();
		}

	}

}
