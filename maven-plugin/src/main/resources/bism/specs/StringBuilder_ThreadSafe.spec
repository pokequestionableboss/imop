pointcut pc1 before MethodCall(* *.StringBuilder.*(..))

event e1("StringBuilder_ThreadSafe", name, [getMethodReceiver, getMethodResult]) on pc1 to Monitor.receiveEvents(String, String, List)
