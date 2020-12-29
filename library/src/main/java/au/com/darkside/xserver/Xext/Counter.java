package au.com.darkside.xserver.Xext;

/**
 * Counter Object. Used as Counter, SystemCounter, Fence and Alarm.
 */
public class Counter {
	public int COUNTER;
	public long value;

	// set if counter is systemcounter
	public short nameLength = 0;
	public byte[] name;
	public String nameStr = "";
	public boolean isSystemCounter = false;

	/**
	 * Constructor.
	 *
	 * @param ID ID of the counter object.
	 * @param initVal initial value.
	 * @param pname name of counter. If set isSystemCounter becomes true.
	 */
	public Counter (int ID, long initVal, String pname) {
		COUNTER=ID;
		value=initVal;
		nameStr = pname;
		name=pname.getBytes();
		nameLength=(short)name.length;
		if(nameLength > 0)
			isSystemCounter = true;
	}

	/**
	 * Constructor.
	 *
	 * @param ID ID of the counter object.
	 * @param initVal initial value.
	 */
	public Counter (int ID, long initVal) {
		COUNTER=ID;
		value=initVal;
	}

	public static class WaitCondition {
		public int counter = 0;
		public int valueType = 0;
		public long waitValue = 0;
		public int testType = 0;
		public long eventThreshold = 0;
		public long testValue = 0;
		public static final int ValueTypeAbsolute = 0;
		public static final int ValueTypeRelative = 1;
		public static final int TestTypePositiveTransition = 0;
		public static final int TestTypeNegativeTransition = 1;
		public static final int TestTypePositiveComparison = 2;
		public static final int TestTypeNegativeComparison = 3;
		public WaitCondition() {};
	}

	public static class AlarmState {
		public static final int Active = 0;
		public static final int Inactive = 1;
		public static final int Destroyed = 2;
	}
}
