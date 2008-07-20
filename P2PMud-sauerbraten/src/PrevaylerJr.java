import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class PrevaylerJr {
	private Object _system;
	private ObjectOutputStream _storage;

	public PrevaylerJr(Object initialState, String storageFile) throws Exception {
		try {
			ObjectInputStream input = new ObjectInputStream(new FileInputStream(storageFile));

			_system = input.readObject();
			while (true) {
				((Command)input.readObject()).executeOn(_system);
			}
		} catch (EOFException expected) {
			//End of journal reached.  :)
		} catch (FileNotFoundException e) {
			_system = initialState;
		}
		_storage = new ObjectOutputStream(new FileOutputStream(storageFile));
		_storage.writeObject(_system);
		_storage.flush();
	}
	synchronized public Object executeTransaction(Command transaction) throws Exception {
		Object result = transaction.executeOn(_system);

		_storage.writeObject(transaction);
		_storage.flush();
		return result;
	}
	synchronized public Object executeQuery(Command query){
		return query.executeOn(_system);
	}
	public static interface Command {
		Object executeOn(Object system);
	}
}
