pointcut pc1 before MethodCall(* *.Iterator.hasNext(..)) || before MethodCall(* *.Iterator.next(..))
pointcut pc2 before MethodCall(* *.*Collection.add*(..)) || before MethodCall(* *.Collection.clear(..)) || before MethodCall(* *.Collection.offer*(..)) || before MethodCall(* *.Collection.pop(..)) || before MethodCall(* *.Collection.push(..)) || before MethodCall(* *.Collection.remove*(..)) || before MethodCall(* *.Collection.retain*(..))
pointcut pc3 after MethodCall(* *.*Iterable.iterator())

event e1("Collection_UnsafeIterator", name, [getMethodReceiver, getMethodResult]) on pc1 to Monitor.receiveEvents(String, String, List)
event e2("Collection_UnsafeIterator", name, [getMethodReceiver, getMethodResult]) on pc2 to Monitor.receiveEvents(String, String, List)
event e3("Collection_UnsafeIterator", name, [getMethodReceiver, getMethodResult]) on pc3 to Monitor.receiveEvents(String, String, List)
