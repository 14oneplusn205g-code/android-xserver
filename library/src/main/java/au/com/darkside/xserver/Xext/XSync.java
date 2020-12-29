package au.com.darkside.xserver.Xext;

import java.util.Hashtable;
import java.util.HashMap;
import java.util.ArrayList;
import java.io.IOException;
import java.lang.Long;

import au.com.darkside.xserver.XServer;
import au.com.darkside.xserver.Client;
import au.com.darkside.xserver.InputOutput;
import au.com.darkside.xserver.Util;
import au.com.darkside.xserver.ErrorCode;
import au.com.darkside.xserver.EventCode;

import android.util.Log;


/**
 * Handles requests related to the X SYNC extension.
 */
public class XSync {
    private static int syncMajor = -1;
    private static int syncMinor;
    private static final Hashtable<String, Counter> _systemCounters = new Hashtable<String, Counter>();
    private static final Hashtable<Integer, Counter> _counters = new Hashtable<Integer, Counter>(); // also includes system counters
    private static final Hashtable<Integer, Counter> _fences = new Hashtable<Integer, Counter>();
    private static final Hashtable<Integer, Counter> _alarms = new Hashtable<Integer, Counter>();


    public static final byte SYNC_INIT = 0;
    public static final byte SYNC_LIST = 1;
    public static final byte SYNC_CREATE = 2;
    public static final byte SYNC_DESTROY = 6;
    public static final byte SYNC_AWAIT = 7;
    public static final byte SYNC_CHANGECOUNTER = 4;
    public static final byte SYNC_SETCOUNTER = 3;
    public static final byte SYNC_QUERYCOUNTER = 5;
    public static final byte SYNC_CREATEALARM = 8;
    public static final byte SYNC_CHANGEALARM = 9;
    public static final byte SYNC_DESTROYALARM = 11;
    public static final byte SYNC_QUERYALARM = 10;
    public static final byte SYNC_SETPRIORITY = 12;
    public static final byte SYNC_GETPRIORITY = 13;
    public static final byte SYNC_CREATEFENCE = 14;
    public static final byte SYNC_TRIGGERFENCE = 15;
    public static final byte SYNC_RESETFENCE = 16;
    public static final byte SYNC_DESTROYFENCE = 17;
    public static final byte SYNC_QUERYFENCE = 18;
    public static final byte SYNC_AWAITFENCE = 19;
    public static final byte EventBase = 95;
    public static final byte CounterNotify = EventBase + 0;
    public static final byte AlarmNotify = EventBase + 1;
    public static final byte ErrorBase = (byte)154;

    // install/init system counters
    static public void Initialize() {
        Counter cntServerTime = new Counter(1, 0, "SERVERTIME");
        _systemCounters.put(cntServerTime.nameStr, cntServerTime);
        _counters.put(cntServerTime.COUNTER, cntServerTime);
    }

    /**
     * Send a counter notify event.
     *
     * @param client       The client to write to.
     * @param ID           ID of the counter.
     * @param waitVal      The value being waited for.
     * @param countVal     Actual value of the counter at the time the event was generated.
     * @param timestamp    Time in milliseconds since last server reset.
     * @param count        If count is 0, there are no more events to follow for this request. If count is n, there are at least n more events to follow.
     * @param destroyed    TRUE if this request was generated as the result of the destruction of the counter and FALSE otherwise.
     * @throws IOException
     */
    public static void sendCounterNotify(Client client, int ID, long waitVal, long countVal, int timestamp, short count, byte destroyed) throws IOException {
        InputOutput io = client.getInputOutput();

        synchronized (io) {
            EventCode.writeHeader(client, CounterNotify, 0);
            io.writeInt(ID); // counter ID
            writeCounterLong(io, waitVal);    // wait value
            writeCounterLong(io, countVal);   // counter value
            io.writeInt(timestamp);  // timestamp
            io.writeShort(count); // count
            io.writeByte(destroyed); // bool destroyed
            io.writePadBytes(1);    // Unused.
        }
        io.flush();
    }

    /**
     * Send a alarm notify event.
     *
     * @param client       The client to write to.
     * @param ID           ID of the alarm.
     * @param countVal     The value of the counter.
     * @param alarmVal     The value of the alarm.
     * @param timestamp    Time in milliseconds since last server reset.
     * @param alarmState   State of the alarm.
     * @throws IOException
     */
    public static void sendAlarmNotify(Client client, int ID, long countVal, long alarmVal, int timestamp, byte alarmState) throws IOException {
        InputOutput io = client.getInputOutput();

        synchronized (io) {
            EventCode.writeHeader(client, AlarmNotify, 1);
            io.writeInt(ID); // alarm ID
            writeCounterLong(io, countVal);    // counter value
            writeCounterLong(io, alarmVal);   // alarm value
            io.writeInt(timestamp);  // timestamp
            io.writeByte(alarmState); // state
            io.writePadBytes(3);    // Unused.
        }
        io.flush();
    }

    /**
     * Reads INT64 fields described in the protocol documentation as long.
     * @param io Input Output object.
     * @return Read value.
     * @throws IOException
     */
    static long readCounterLong(InputOutput io) throws IOException{
        int hi = io.readInt();
        int lo = io.readInt();
        long ret = lo;
        ret |= (long)hi << 32;
        return ret;
    }

    /**
     * Writes long values as INT64 fields like described in the protocol.
     * @param io Input Output object.
     * @param val Value to write.
     * @throws IOException
     */
    static void writeCounterLong(InputOutput io, long val) throws IOException{
        int hi = (int)(val >> 32);
        int lo = (int)val;
        io.writeInt(hi);
        io.writeInt(lo);
    }

    /**
     * Process a request relating to the X SYNC extension.
     *
     * @param xServer        The X server.
     * @param client         The remote client.
     * @param opcode         The request's opcode.
     * @param arg            Optional first argument.
     * @param bytesRemaining Bytes yet to be read in the request.
     * @throws IOException
     */
    static public void processRequest(XServer xServer, Client client, byte opcode, byte arg, int bytesRemaining) throws java.io.IOException {
        InputOutput io=client.getInputOutput();
        _systemCounters.get("SERVERTIME").value = System.currentTimeMillis() - xServer.getLastResetTimestamp();
        switch(arg) {
            case SYNC_INIT:
                if (bytesRemaining < 2) {
                    io.readSkip (bytesRemaining);
                    ErrorCode.write (client, ErrorCode.Length, opcode, 0);
                } else {
                    syncMajor=io.readByte();
                    syncMinor=io.readByte();
                    bytesRemaining-=2;
                    io.readSkip (bytesRemaining);
                    synchronized(io) {
                        Util.writeReplyHeader(client, arg);
                        io.writeInt (0);	// Reply length.
                        io.writeByte((byte)syncMajor);
                        io.writeByte((byte)syncMinor);
                        io.writePadBytes(22);
                    }
                    io.flush ();
                }
                break;
            case SYNC_LIST:
                io.readSkip(bytesRemaining);
                int n = _systemCounters.size();//number of counters
                int length = 0;
                for(String key : _systemCounters.keySet()) {
                    int temp=(key.length()+2)%4;
                    if(temp==0)
                        temp=4;
                    length+=14+key.length()+(4-temp);
                }
                int len=length%32;
                length/=32;
                if(len>0)
                    length++;
                else
                    len=32;
                length*=8;
                synchronized(io) {
                    Util.writeReplyHeader(client, arg);
                    io.writeInt (length);	// Reply length.
                    io.writeInt (n);	// List length.
                    io.writePadBytes(20); //unused
                    for(Counter count : _systemCounters.values()) {
                        /* 4 COUNTER counter
                           8 INT64   resolution
                           2 n       length of name in bytes
                           n STRING8 name
                           p         pad,p=pad(n+2) */
                        io.writeInt(count.COUNTER);
                        writeCounterLong(io, count.value);
                        io.writeShort(count.nameLength);
                        io.writeBytes(count.name,0,count.name.length);
                        int pad=(count.name.length+2)%4;
                        if(pad==0)
                            pad=4;
                        io.writePadBytes(4-pad);
                    }
                    io.writePadBytes(32-(len));
                }
                io.flush();
                break;
            case SYNC_CREATE:
                if (bytesRemaining != 12) {
                    io.readSkip(bytesRemaining);
                    ErrorCode.write(client, ErrorCode.Length, opcode, 0);
                } else {
                    synchronized(_counters){
                        int ID=io.readInt();
                        long initVal=readCounterLong(io);
                        bytesRemaining-=12;
                        _counters.put(ID, new Counter(ID,initVal));
                    }
                }
                break;
            case SYNC_DESTROY:
                if (bytesRemaining != 4) {
                    io.readSkip (bytesRemaining);
                    ErrorCode.write (client, ErrorCode.Length, opcode, 0);
                } else {
                    synchronized(_counters){
                        int ID=io.readInt();
                        bytesRemaining-=4;
                        Counter count=_counters.remove(ID);
                        if(count!=null) {
                            synchronized(io) {
                                Util.writeReplyHeader(client, arg);
                                io.writeInt(0);
                                writeCounterLong(io, count.value);
                                io.writePadBytes(16);
                            }
                            io.flush();
                        }
                        else {
                            ErrorCode.writeWithMinorOpcode(client, ErrorBase, arg, opcode, ID);
                        }
                    }
                }
                break;
            case SYNC_CHANGECOUNTER:
                if (bytesRemaining != 12) {
                    io.readSkip(bytesRemaining);
                    ErrorCode.write(client, ErrorCode.Length, opcode, 0);
                } else {
                    int ID = io.readInt();
                    long value = readCounterLong(io);
                    bytesRemaining -= 12;
                    Counter count = _counters.get(ID);
                    if(count!=null) {
                        if(count.isSystemCounter)
                            ErrorCode.writeWithMinorOpcode(client, ErrorCode.Access, arg, opcode, ID);
                        else
                            _counters.get(ID).value += value;
                    }
                    else {
                        ErrorCode.writeWithMinorOpcode(client, ErrorBase, arg, opcode, ID);
                    }
                } 
                break;
            case SYNC_SETCOUNTER: 
                if (bytesRemaining != 12) {
                    io.readSkip(bytesRemaining);
                    ErrorCode.write(client, ErrorCode.Length, opcode, 0);
                } else {
                    int ID = io.readInt();
                    long value = readCounterLong(io);
                    bytesRemaining -= 12;
                    Counter count = _counters.get(ID);
                    if(count!=null) {
                        if(count.isSystemCounter)
                            ErrorCode.writeWithMinorOpcode(client, ErrorCode.Access, arg, opcode, ID);
                        else
                            _counters.get(ID).value = value;
                    }
                    else {
                        ErrorCode.writeWithMinorOpcode(client, ErrorBase, arg, opcode, ID);
                    }
                } 
                break;
            case SYNC_QUERYCOUNTER: 
                if (bytesRemaining != 4) {
                    io.readSkip(bytesRemaining);
                    ErrorCode.write(client, ErrorCode.Length, opcode, 0);
                } else {
                    int ID = io.readInt();
                    bytesRemaining -= 4;
                    Counter count = _counters.get(ID);
                    if(count!=null) {
                        synchronized(io) {
                            Util.writeReplyHeader(client, arg);
                            io.writeInt(0);
                            writeCounterLong(io, count.value);
                            io.writePadBytes(16);
                        }
                        io.flush();
                    }
                    else {
                        ErrorCode.writeWithMinorOpcode(client, ErrorBase, arg, opcode, ID);
                    }
                } 
                break;
            case SYNC_AWAIT: 
                ArrayList<Counter.WaitCondition> waitConditions = new ArrayList<Counter.WaitCondition>();
                // parse wait conditions
                while(bytesRemaining > 0){
                    Counter.WaitCondition cond = new Counter.WaitCondition();
                    cond.counter = io.readInt(); // counter id
                    cond.valueType = io.readInt(); // value-type {0=Absolute,1=Relative};
                    cond.waitValue = readCounterLong(io); // wait-value
                    cond.testType = io.readInt(); // test-type {0=PositiveTransition,1=NegativeTransition,2=PositiveComparison,3=NegativeComparison}
                    cond.eventThreshold = readCounterLong(io); // event-threshhold
                    waitConditions.add(cond);
                    bytesRemaining-=28;
                }
                boolean allCountersExist = false;
                HashMap<Integer, Counter> localCpy = new HashMap<Integer, Counter>();
                for(;;){ // block until one condition is met
                    for(Counter.WaitCondition cond : waitConditions){
                        synchronized(_counters){
                            Counter counter = _counters.get(cond.counter);
                            if(counter == null && allCountersExist){ // counter was destroyed
                                sendCounterNotify(client, cond.counter, cond.testValue, localCpy.get(cond.counter).value, xServer.getTimestamp(), (short)0, (byte)1);
                                return;
                            }
                            if(counter!=null) {
                                if(cond.testValue == 0){
                                    cond.testValue = cond.waitValue;
                                    if(cond.valueType == Counter.WaitCondition.ValueTypeRelative)
                                        cond.testValue = counter.value + cond.waitValue;
                                }
                                long diff = counter.value - cond.testValue;
                                if((cond.testType == Counter.WaitCondition.TestTypePositiveTransition || cond.testType == Counter.WaitCondition.TestTypePositiveComparison) && counter.value >= cond.testValue){
                                    if(Long.compareUnsigned(diff,cond.eventThreshold) >= 0)
                                        sendCounterNotify(client, cond.counter, cond.testValue, counter.value, xServer.getTimestamp(), (short)0, (byte)0);
                                    return;
                                }
                                else if ((cond.testType == Counter.WaitCondition.TestTypeNegativeTransition || cond.testType == Counter.WaitCondition.TestTypeNegativeComparison) && counter.value <= cond.testValue){
                                    if(Long.compareUnsigned(diff,cond.eventThreshold) <= 0)
                                        sendCounterNotify(client, cond.counter, cond.testValue, counter.value, xServer.getTimestamp(), (short)0, (byte)0);
                                    return;
                                }
                                else if(!localCpy.containsKey(cond.counter))
                                    localCpy.put(cond.counter, counter);
                            }
                            else {
                                ErrorCode.writeWithMinorOpcode(client, ErrorBase, arg, opcode, cond.counter);
                                return;
                            }
                        }
                    }
                    allCountersExist = true;
                    _systemCounters.get("SERVERTIME").value = System.currentTimeMillis() - xServer.getLastResetTimestamp();
                }
            case SYNC_CREATEFENCE:
                if (bytesRemaining != 12) {
                    io.readSkip(bytesRemaining);
                    ErrorCode.write(client, ErrorCode.Length, opcode, 0);
                } else {
                    synchronized(_fences){
                        int drawable = io.readInt(); // should be used to bind this fence to a certain display
                        int ID = io.readInt();
                        byte initVal = (byte)io.readByte();
                        io.readSkip(3); // unused
                        bytesRemaining-=12;
                        _fences.put(ID, new Counter(ID,(long)initVal));
                    }
                } 
                break;
            case SYNC_TRIGGERFENCE:
                if (bytesRemaining != 4) {
                    io.readSkip(bytesRemaining);
                    ErrorCode.write(client, ErrorCode.Length, opcode, 0);
                } else {
                    int ID = io.readInt();
                    bytesRemaining -= 4;
                    Counter fence = _fences.get(ID);
                    if(fence!=null) {
                        _fences.get(ID).value = 1;
                    }
                    else {
                        ErrorCode.writeWithMinorOpcode(client, ErrorBase, arg, opcode, ID);
                    }
                } 
                break;
            case SYNC_RESETFENCE:
                if (bytesRemaining != 4) {
                    io.readSkip(bytesRemaining);
                    ErrorCode.write(client, ErrorCode.Length, opcode, 0);
                } else {
                    int ID = io.readInt();
                    bytesRemaining -= 4;
                    Counter fence = _fences.get(ID);
                    if(fence!=null) {
                        _fences.get(ID).value = 0;
                    }
                    else {
                        ErrorCode.writeWithMinorOpcode(client, ErrorBase, arg, opcode, ID);
                    }
                } 
                break;
            case SYNC_DESTROYFENCE:
                if (bytesRemaining != 4) {
                    io.readSkip(bytesRemaining);
                    ErrorCode.write(client, ErrorCode.Length, opcode, 0);
                } else {
                    synchronized(_fences){
                        int ID = io.readInt();
                        bytesRemaining -= 4;
                        Counter fence = _fences.remove(ID);
                        if(fence==null) {
                            ErrorCode.writeWithMinorOpcode(client, ErrorBase, arg, opcode, ID);
                        }
                    }
                } 
                break;
            case SYNC_QUERYFENCE:
                if (bytesRemaining != 4) {
                    io.readSkip(bytesRemaining);
                    ErrorCode.write(client, ErrorCode.Length, opcode, 0);
                } else {
                    int ID = io.readInt();
                    bytesRemaining -= 4;
                    Counter fence = _fences.get(ID);
                    if(fence!=null) {
                        synchronized(io) {
                            Util.writeReplyHeader(client, arg);
                            io.writeInt(0);
                            io.writeByte((byte)fence.value);
                            io.writePadBytes(23);
                        }
                        io.flush();
                    }
                    else {
                        ErrorCode.writeWithMinorOpcode(client, ErrorBase, arg, opcode, ID);
                    }
                } 
                break;
            case SYNC_AWAITFENCE: 
                if (bytesRemaining < 4) {
                    io.readSkip(bytesRemaining);
                    ErrorCode.write(client, ErrorCode.Length, opcode, 0);
                } else {
                    ArrayList<Integer> IDs = new ArrayList<Integer>();
                    while(bytesRemaining > 0){
                        IDs.add(io.readInt());
                        bytesRemaining-=4;
                    }
                    boolean allFencesExist = false;
                    for(;;){ // block until one of the fences enteres triggered state
                        for(Integer ID : IDs){
                            synchronized(_fences){
                                Counter fence = _fences.get(ID);
                                if(fence == null && allFencesExist) // fence was removed
                                    return;
                                if(fence!=null) {
                                    if(fence.value == 1)
                                        return;
                                }
                                else {
                                    ErrorCode.writeWithMinorOpcode(client, ErrorBase, arg, opcode, ID);
                                    return;
                                }
                            }
                        }
                        allFencesExist = true;
                        _systemCounters.get("SERVERTIME").value = System.currentTimeMillis() - xServer.getLastResetTimestamp();
                    }
                }
                break;
            /*case SYNC_CREATEALARM: break;
            case SYNC_CHANGEALARM: break;
            case SYNC_DESTROYALARM:
                if (bytesRemaining != 4) {
                    io.readSkip(bytesRemaining);
                    ErrorCode.write(client, ErrorCode.Length, opcode, 0);
                } else {
                    // TODO
                } 
                break;
            case SYNC_QUERYALARM:
                if (bytesRemaining != 4) {
                    io.readSkip(bytesRemaining);
                    ErrorCode.write(client, ErrorCode.Length, opcode, 0);
                } else {
                    // TODO
                } 
                break;
            case SYNC_SETPRIORITY:
                if (bytesRemaining != 8) {
                    io.readSkip(bytesRemaining);
                    ErrorCode.write(client, ErrorCode.Length, opcode, 0);
                } else {
                    // TODO
                } 
                break;
            case SYNC_GETPRIORITY: 
                // todo
                break;*/
            default:
                io.readSkip(bytesRemaining);    // Not implemented.
                ErrorCode.write(client, ErrorCode.Implementation, opcode, 0);
                break;
        }
    }
}
