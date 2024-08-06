pointcut pc1 before MethodCall(* *.System.arraycopy(Object, int, Object, int, int))

event e1("System_NullArrayCopy", name, [getMethodReceiver, getMethodResult, getAllMethodArgs]) on pc1 to Monitor.receiveEvents(String, String, List)
