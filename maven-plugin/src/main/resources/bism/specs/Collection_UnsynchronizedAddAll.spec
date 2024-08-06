pointcut pc1 before MethodCall(* *.*Collection.add*(..)) || before MethodCall(* *.*Collection.remove*(..)) || before MethodCall(* *.*Collection.clear(..)) || before MethodCall(* *.*Collection.retain*(..))
pointcut pc2 before MethodCall(* *.*Collection.addAll(..))
pointcut pc2 after MethodCall(* *.*Collection.addAll(..))

event e1("Collection_UnsynchronizedAddAll", name, [getMethodReceiver, getMethodResult, getAllMethodArgs]) on pc1 to Monitor.receiveEvents(String, String, List)
event e2("Collection_UnsynchronizedAddAll", name, [getMethodReceiver, getMethodResult]) on pc2 to Monitor.receiveEvents(String, String, List)
event e3("Collection_UnsynchronizedAddAll", name, [getMethodReceiver, getMethodResult]) on pc3 to Monitor.receiveEvents(String, String, List)
