package kvClient.pb;

import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ClientCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ClientCalls.asyncClientStreamingCall;
import static io.grpc.stub.ClientCalls.asyncServerStreamingCall;
import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.blockingServerStreamingCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ServerCalls.asyncClientStreamingCall;
import static io.grpc.stub.ServerCalls.asyncServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;

import io.grpc.BindableService;
import io.grpc.ServerServiceDefinition;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.18.0)",
    comments = "Source: txn.proto")
public final class KvServiceGrpc {

  private KvServiceGrpc() {}
  
  public static final String SERVICE_NAME = "pb.KvService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<kvClient.pb.BeginArg,
      kvClient.pb.Response> getBeginMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Begin",
      requestType = kvClient.pb.BeginArg.class,
      responseType = kvClient.pb.Response.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<kvClient.pb.BeginArg,
      kvClient.pb.Response> getBeginMethod() {
    io.grpc.MethodDescriptor<kvClient.pb.BeginArg, kvClient.pb.Response> getBeginMethod;
    if ((getBeginMethod = KvServiceGrpc.getBeginMethod) == null) {
      synchronized (KvServiceGrpc.class) {
        if ((getBeginMethod = KvServiceGrpc.getBeginMethod) == null) {
          KvServiceGrpc.getBeginMethod = getBeginMethod = 
              io.grpc.MethodDescriptor.<kvClient.pb.BeginArg, kvClient.pb.Response>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "pb.KvService", "Begin"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  kvClient.pb.BeginArg.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  kvClient.pb.Response.getDefaultInstance()))
                  .setSchemaDescriptor(new KvServiceMethodDescriptorSupplier("Begin"))
                  .build();
          }
        }
     }
     return getBeginMethod;
  }

  private static volatile io.grpc.MethodDescriptor<kvClient.pb.CommitArg,
      kvClient.pb.Response> getCommitMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Commit",
      requestType = kvClient.pb.CommitArg.class,
      responseType = kvClient.pb.Response.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<kvClient.pb.CommitArg,
      kvClient.pb.Response> getCommitMethod() {
    io.grpc.MethodDescriptor<kvClient.pb.CommitArg, kvClient.pb.Response> getCommitMethod;
    if ((getCommitMethod = KvServiceGrpc.getCommitMethod) == null) {
      synchronized (KvServiceGrpc.class) {
        if ((getCommitMethod = KvServiceGrpc.getCommitMethod) == null) {
          KvServiceGrpc.getCommitMethod = getCommitMethod = 
              io.grpc.MethodDescriptor.<kvClient.pb.CommitArg, kvClient.pb.Response>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "pb.KvService", "Commit"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  kvClient.pb.CommitArg.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  kvClient.pb.Response.getDefaultInstance()))
                  .setSchemaDescriptor(new KvServiceMethodDescriptorSupplier("Commit"))
                  .build();
          }
        }
     }
     return getCommitMethod;
  }

  private static volatile io.grpc.MethodDescriptor<kvClient.pb.AbortArg,
      kvClient.pb.Response> getAbortMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Abort",
      requestType = kvClient.pb.AbortArg.class,
      responseType = kvClient.pb.Response.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<kvClient.pb.AbortArg,
      kvClient.pb.Response> getAbortMethod() {
    io.grpc.MethodDescriptor<kvClient.pb.AbortArg, kvClient.pb.Response> getAbortMethod;
    if ((getAbortMethod = KvServiceGrpc.getAbortMethod) == null) {
      synchronized (KvServiceGrpc.class) {
        if ((getAbortMethod = KvServiceGrpc.getAbortMethod) == null) {
          KvServiceGrpc.getAbortMethod = getAbortMethod = 
              io.grpc.MethodDescriptor.<kvClient.pb.AbortArg, kvClient.pb.Response>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "pb.KvService", "Abort"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  kvClient.pb.AbortArg.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  kvClient.pb.Response.getDefaultInstance()))
                  .setSchemaDescriptor(new KvServiceMethodDescriptorSupplier("Abort"))
                  .build();
          }
        }
     }
     return getAbortMethod;
  }

  private static volatile io.grpc.MethodDescriptor<kvClient.pb.RollbackArg,
      kvClient.pb.Response> getRollbackMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Rollback",
      requestType = kvClient.pb.RollbackArg.class,
      responseType = kvClient.pb.Response.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<kvClient.pb.RollbackArg,
      kvClient.pb.Response> getRollbackMethod() {
    io.grpc.MethodDescriptor<kvClient.pb.RollbackArg, kvClient.pb.Response> getRollbackMethod;
    if ((getRollbackMethod = KvServiceGrpc.getRollbackMethod) == null) {
      synchronized (KvServiceGrpc.class) {
        if ((getRollbackMethod = KvServiceGrpc.getRollbackMethod) == null) {
          KvServiceGrpc.getRollbackMethod = getRollbackMethod = 
              io.grpc.MethodDescriptor.<kvClient.pb.RollbackArg, kvClient.pb.Response>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "pb.KvService", "Rollback"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  kvClient.pb.RollbackArg.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  kvClient.pb.Response.getDefaultInstance()))
                  .setSchemaDescriptor(new KvServiceMethodDescriptorSupplier("Rollback"))
                  .build();
          }
        }
     }
     return getRollbackMethod;
  }

  private static volatile io.grpc.MethodDescriptor<kvClient.pb.IsAliveArg,
      kvClient.pb.Response> getIsAliveMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "IsAlive",
      requestType = kvClient.pb.IsAliveArg.class,
      responseType = kvClient.pb.Response.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<kvClient.pb.IsAliveArg,
      kvClient.pb.Response> getIsAliveMethod() {
    io.grpc.MethodDescriptor<kvClient.pb.IsAliveArg, kvClient.pb.Response> getIsAliveMethod;
    if ((getIsAliveMethod = KvServiceGrpc.getIsAliveMethod) == null) {
      synchronized (KvServiceGrpc.class) {
        if ((getIsAliveMethod = KvServiceGrpc.getIsAliveMethod) == null) {
          KvServiceGrpc.getIsAliveMethod = getIsAliveMethod = 
              io.grpc.MethodDescriptor.<kvClient.pb.IsAliveArg, kvClient.pb.Response>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "pb.KvService", "IsAlive"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  kvClient.pb.IsAliveArg.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  kvClient.pb.Response.getDefaultInstance()))
                  .setSchemaDescriptor(new KvServiceMethodDescriptorSupplier("IsAlive"))
                  .build();
          }
        }
     }
     return getIsAliveMethod;
  }

  private static volatile io.grpc.MethodDescriptor<kvClient.pb.InsertArg,
      kvClient.pb.Response> getInsertMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Insert",
      requestType = kvClient.pb.InsertArg.class,
      responseType = kvClient.pb.Response.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<kvClient.pb.InsertArg,
      kvClient.pb.Response> getInsertMethod() {
    io.grpc.MethodDescriptor<kvClient.pb.InsertArg, kvClient.pb.Response> getInsertMethod;
    if ((getInsertMethod = KvServiceGrpc.getInsertMethod) == null) {
      synchronized (KvServiceGrpc.class) {
        if ((getInsertMethod = KvServiceGrpc.getInsertMethod) == null) {
          KvServiceGrpc.getInsertMethod = getInsertMethod = 
              io.grpc.MethodDescriptor.<kvClient.pb.InsertArg, kvClient.pb.Response>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "pb.KvService", "Insert"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  kvClient.pb.InsertArg.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  kvClient.pb.Response.getDefaultInstance()))
                  .setSchemaDescriptor(new KvServiceMethodDescriptorSupplier("Insert"))
                  .build();
          }
        }
     }
     return getInsertMethod;
  }

  private static volatile io.grpc.MethodDescriptor<kvClient.pb.DeleteArg,
      kvClient.pb.Response> getDeleteMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Delete",
      requestType = kvClient.pb.DeleteArg.class,
      responseType = kvClient.pb.Response.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<kvClient.pb.DeleteArg,
      kvClient.pb.Response> getDeleteMethod() {
    io.grpc.MethodDescriptor<kvClient.pb.DeleteArg, kvClient.pb.Response> getDeleteMethod;
    if ((getDeleteMethod = KvServiceGrpc.getDeleteMethod) == null) {
      synchronized (KvServiceGrpc.class) {
        if ((getDeleteMethod = KvServiceGrpc.getDeleteMethod) == null) {
          KvServiceGrpc.getDeleteMethod = getDeleteMethod = 
              io.grpc.MethodDescriptor.<kvClient.pb.DeleteArg, kvClient.pb.Response>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "pb.KvService", "Delete"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  kvClient.pb.DeleteArg.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  kvClient.pb.Response.getDefaultInstance()))
                  .setSchemaDescriptor(new KvServiceMethodDescriptorSupplier("Delete"))
                  .build();
          }
        }
     }
     return getDeleteMethod;
  }

  private static volatile io.grpc.MethodDescriptor<kvClient.pb.GetArg,
      kvClient.pb.ResponseKv> getGetMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Get",
      requestType = kvClient.pb.GetArg.class,
      responseType = kvClient.pb.ResponseKv.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<kvClient.pb.GetArg,
      kvClient.pb.ResponseKv> getGetMethod() {
    io.grpc.MethodDescriptor<kvClient.pb.GetArg, kvClient.pb.ResponseKv> getGetMethod;
    if ((getGetMethod = KvServiceGrpc.getGetMethod) == null) {
      synchronized (KvServiceGrpc.class) {
        if ((getGetMethod = KvServiceGrpc.getGetMethod) == null) {
          KvServiceGrpc.getGetMethod = getGetMethod = 
              io.grpc.MethodDescriptor.<kvClient.pb.GetArg, kvClient.pb.ResponseKv>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "pb.KvService", "Get"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  kvClient.pb.GetArg.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  kvClient.pb.ResponseKv.getDefaultInstance()))
                  .setSchemaDescriptor(new KvServiceMethodDescriptorSupplier("Get"))
                  .build();
          }
        }
     }
     return getGetMethod;
  }

  private static volatile io.grpc.MethodDescriptor<kvClient.pb.SetArg,
      kvClient.pb.Response> getSetMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Set",
      requestType = kvClient.pb.SetArg.class,
      responseType = kvClient.pb.Response.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<kvClient.pb.SetArg,
      kvClient.pb.Response> getSetMethod() {
    io.grpc.MethodDescriptor<kvClient.pb.SetArg, kvClient.pb.Response> getSetMethod;
    if ((getSetMethod = KvServiceGrpc.getSetMethod) == null) {
      synchronized (KvServiceGrpc.class) {
        if ((getSetMethod = KvServiceGrpc.getSetMethod) == null) {
          KvServiceGrpc.getSetMethod = getSetMethod = 
              io.grpc.MethodDescriptor.<kvClient.pb.SetArg, kvClient.pb.Response>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "pb.KvService", "Set"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  kvClient.pb.SetArg.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  kvClient.pb.Response.getDefaultInstance()))
                  .setSchemaDescriptor(new KvServiceMethodDescriptorSupplier("Set"))
                  .build();
          }
        }
     }
     return getSetMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static KvServiceStub newStub(io.grpc.Channel channel) {
    return new KvServiceStub(channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static KvServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new KvServiceBlockingStub(channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static KvServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new KvServiceFutureStub(channel);
  }

  /**
   */
  public static class KvServiceImplBase implements io.grpc.BindableService {

    /**
     */
    public void begin(kvClient.pb.BeginArg request,
        io.grpc.stub.StreamObserver<kvClient.pb.Response> responseObserver) {
      asyncUnimplementedUnaryCall(getBeginMethod(), responseObserver);
    }

    /**
     */
    public void commit(kvClient.pb.CommitArg request,
        io.grpc.stub.StreamObserver<kvClient.pb.Response> responseObserver) {
      asyncUnimplementedUnaryCall(getCommitMethod(), responseObserver);
    }

    /**
     */
    public void abort(kvClient.pb.AbortArg request,
        io.grpc.stub.StreamObserver<kvClient.pb.Response> responseObserver) {
      asyncUnimplementedUnaryCall(getAbortMethod(), responseObserver);
    }

    /**
     */
    public void rollback(kvClient.pb.RollbackArg request,
        io.grpc.stub.StreamObserver<kvClient.pb.Response> responseObserver) {
      asyncUnimplementedUnaryCall(getRollbackMethod(), responseObserver);
    }

    /**
     */
    public void isAlive(kvClient.pb.IsAliveArg request,
        io.grpc.stub.StreamObserver<kvClient.pb.Response> responseObserver) {
      asyncUnimplementedUnaryCall(getIsAliveMethod(), responseObserver);
    }

    /**
     */
    public void insert(kvClient.pb.InsertArg request,
        io.grpc.stub.StreamObserver<kvClient.pb.Response> responseObserver) {
      asyncUnimplementedUnaryCall(getInsertMethod(), responseObserver);
    }

    /**
     */
    public void delete(kvClient.pb.DeleteArg request,
        io.grpc.stub.StreamObserver<kvClient.pb.Response> responseObserver) {
      asyncUnimplementedUnaryCall(getDeleteMethod(), responseObserver);
    }

    /**
     */
    public void get(kvClient.pb.GetArg request,
        io.grpc.stub.StreamObserver<kvClient.pb.ResponseKv> responseObserver) {
      asyncUnimplementedUnaryCall(getGetMethod(), responseObserver);
    }

    /**
     */
    public void set(kvClient.pb.SetArg request,
        io.grpc.stub.StreamObserver<kvClient.pb.Response> responseObserver) {
      asyncUnimplementedUnaryCall(getSetMethod(), responseObserver);
    }

    public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getBeginMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                kvClient.pb.BeginArg,
                kvClient.pb.Response>(
                  this, METHODID_BEGIN)))
          .addMethod(
            getCommitMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                kvClient.pb.CommitArg,
                kvClient.pb.Response>(
                  this, METHODID_COMMIT)))
          .addMethod(
            getAbortMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                kvClient.pb.AbortArg,
                kvClient.pb.Response>(
                  this, METHODID_ABORT)))
          .addMethod(
            getRollbackMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                kvClient.pb.RollbackArg,
                kvClient.pb.Response>(
                  this, METHODID_ROLLBACK)))
          .addMethod(
            getIsAliveMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                kvClient.pb.IsAliveArg,
                kvClient.pb.Response>(
                  this, METHODID_IS_ALIVE)))
          .addMethod(
            getInsertMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                kvClient.pb.InsertArg,
                kvClient.pb.Response>(
                  this, METHODID_INSERT)))
          .addMethod(
            getDeleteMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                kvClient.pb.DeleteArg,
                kvClient.pb.Response>(
                  this, METHODID_DELETE)))
          .addMethod(
            getGetMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                kvClient.pb.GetArg,
                kvClient.pb.ResponseKv>(
                  this, METHODID_GET)))
          .addMethod(
            getSetMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                kvClient.pb.SetArg,
                kvClient.pb.Response>(
                  this, METHODID_SET)))
          .build();
    }
  }

  /**
   */
  public static final class KvServiceStub extends io.grpc.stub.AbstractStub<KvServiceStub> {
    private KvServiceStub(io.grpc.Channel channel) {
      super(channel);
    }

    private KvServiceStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected KvServiceStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new KvServiceStub(channel, callOptions);
    }

    /**
     */
    public void begin(kvClient.pb.BeginArg request,
        io.grpc.stub.StreamObserver<kvClient.pb.Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getBeginMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void commit(kvClient.pb.CommitArg request,
        io.grpc.stub.StreamObserver<kvClient.pb.Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getCommitMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void abort(kvClient.pb.AbortArg request,
        io.grpc.stub.StreamObserver<kvClient.pb.Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getAbortMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void rollback(kvClient.pb.RollbackArg request,
        io.grpc.stub.StreamObserver<kvClient.pb.Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getRollbackMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void isAlive(kvClient.pb.IsAliveArg request,
        io.grpc.stub.StreamObserver<kvClient.pb.Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getIsAliveMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void insert(kvClient.pb.InsertArg request,
        io.grpc.stub.StreamObserver<kvClient.pb.Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getInsertMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void delete(kvClient.pb.DeleteArg request,
        io.grpc.stub.StreamObserver<kvClient.pb.Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getDeleteMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void get(kvClient.pb.GetArg request,
        io.grpc.stub.StreamObserver<kvClient.pb.ResponseKv> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getGetMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void set(kvClient.pb.SetArg request,
        io.grpc.stub.StreamObserver<kvClient.pb.Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getSetMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   */
  public static final class KvServiceBlockingStub extends io.grpc.stub.AbstractStub<KvServiceBlockingStub> {
    private KvServiceBlockingStub(io.grpc.Channel channel) {
      super(channel);
    }

    private KvServiceBlockingStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected KvServiceBlockingStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new KvServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public kvClient.pb.Response begin(kvClient.pb.BeginArg request) {
      return blockingUnaryCall(
          getChannel(), getBeginMethod(), getCallOptions(), request);
    }

    /**
     */
    public kvClient.pb.Response commit(kvClient.pb.CommitArg request) {
      return blockingUnaryCall(
          getChannel(), getCommitMethod(), getCallOptions(), request);
    }

    /**
     */
    public kvClient.pb.Response abort(kvClient.pb.AbortArg request) {
      return blockingUnaryCall(
          getChannel(), getAbortMethod(), getCallOptions(), request);
    }

    /**
     */
    public kvClient.pb.Response rollback(kvClient.pb.RollbackArg request) {
      return blockingUnaryCall(
          getChannel(), getRollbackMethod(), getCallOptions(), request);
    }

    /**
     */
    public kvClient.pb.Response isAlive(kvClient.pb.IsAliveArg request) {
      return blockingUnaryCall(
          getChannel(), getIsAliveMethod(), getCallOptions(), request);
    }

    /**
     */
    public kvClient.pb.Response insert(kvClient.pb.InsertArg request) {
      return blockingUnaryCall(
          getChannel(), getInsertMethod(), getCallOptions(), request);
    }

    /**
     */
    public kvClient.pb.Response delete(kvClient.pb.DeleteArg request) {
      return blockingUnaryCall(
          getChannel(), getDeleteMethod(), getCallOptions(), request);
    }

    /**
     */
    public kvClient.pb.ResponseKv get(kvClient.pb.GetArg request) {
      return blockingUnaryCall(
          getChannel(), getGetMethod(), getCallOptions(), request);
    }

    /**
     */
    public kvClient.pb.Response set(kvClient.pb.SetArg request) {
      return blockingUnaryCall(
          getChannel(), getSetMethod(), getCallOptions(), request);
    }
  }

  /**
   */
  public static final class KvServiceFutureStub extends io.grpc.stub.AbstractStub<KvServiceFutureStub> {
    private KvServiceFutureStub(io.grpc.Channel channel) {
      super(channel);
    }

    private KvServiceFutureStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected KvServiceFutureStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new KvServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<kvClient.pb.Response> begin(
        kvClient.pb.BeginArg request) {
      return futureUnaryCall(
          getChannel().newCall(getBeginMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<kvClient.pb.Response> commit(
        kvClient.pb.CommitArg request) {
      return futureUnaryCall(
          getChannel().newCall(getCommitMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<kvClient.pb.Response> abort(
        kvClient.pb.AbortArg request) {
      return futureUnaryCall(
          getChannel().newCall(getAbortMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<kvClient.pb.Response> rollback(
        kvClient.pb.RollbackArg request) {
      return futureUnaryCall(
          getChannel().newCall(getRollbackMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<kvClient.pb.Response> isAlive(
        kvClient.pb.IsAliveArg request) {
      return futureUnaryCall(
          getChannel().newCall(getIsAliveMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<kvClient.pb.Response> insert(
        kvClient.pb.InsertArg request) {
      return futureUnaryCall(
          getChannel().newCall(getInsertMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<kvClient.pb.Response> delete(
        kvClient.pb.DeleteArg request) {
      return futureUnaryCall(
          getChannel().newCall(getDeleteMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<kvClient.pb.ResponseKv> get(
        kvClient.pb.GetArg request) {
      return futureUnaryCall(
          getChannel().newCall(getGetMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<kvClient.pb.Response> set(
        kvClient.pb.SetArg request) {
      return futureUnaryCall(
          getChannel().newCall(getSetMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_BEGIN = 0;
  private static final int METHODID_COMMIT = 1;
  private static final int METHODID_ABORT = 2;
  private static final int METHODID_ROLLBACK = 3;
  private static final int METHODID_IS_ALIVE = 4;
  private static final int METHODID_INSERT = 5;
  private static final int METHODID_DELETE = 6;
  private static final int METHODID_GET = 7;
  private static final int METHODID_SET = 8;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final KvServiceImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(KvServiceImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_BEGIN:
          serviceImpl.begin((kvClient.pb.BeginArg) request,
              (io.grpc.stub.StreamObserver<kvClient.pb.Response>) responseObserver);
          break;
        case METHODID_COMMIT:
          serviceImpl.commit((kvClient.pb.CommitArg) request,
              (io.grpc.stub.StreamObserver<kvClient.pb.Response>) responseObserver);
          break;
        case METHODID_ABORT:
          serviceImpl.abort((kvClient.pb.AbortArg) request,
              (io.grpc.stub.StreamObserver<kvClient.pb.Response>) responseObserver);
          break;
        case METHODID_ROLLBACK:
          serviceImpl.rollback((kvClient.pb.RollbackArg) request,
              (io.grpc.stub.StreamObserver<kvClient.pb.Response>) responseObserver);
          break;
        case METHODID_IS_ALIVE:
          serviceImpl.isAlive((kvClient.pb.IsAliveArg) request,
              (io.grpc.stub.StreamObserver<kvClient.pb.Response>) responseObserver);
          break;
        case METHODID_INSERT:
          serviceImpl.insert((kvClient.pb.InsertArg) request,
              (io.grpc.stub.StreamObserver<kvClient.pb.Response>) responseObserver);
          break;
        case METHODID_DELETE:
          serviceImpl.delete((kvClient.pb.DeleteArg) request,
              (io.grpc.stub.StreamObserver<kvClient.pb.Response>) responseObserver);
          break;
        case METHODID_GET:
          serviceImpl.get((kvClient.pb.GetArg) request,
              (io.grpc.stub.StreamObserver<kvClient.pb.ResponseKv>) responseObserver);
          break;
        case METHODID_SET:
          serviceImpl.set((kvClient.pb.SetArg) request,
              (io.grpc.stub.StreamObserver<kvClient.pb.Response>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  private static abstract class KvServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    KvServiceBaseDescriptorSupplier() {}

    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return kvClient.pb.TxnPB.getDescriptor();
    }

    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("KvService");
    }
  }

  private static final class KvServiceFileDescriptorSupplier
      extends KvServiceBaseDescriptorSupplier {
    KvServiceFileDescriptorSupplier() {}
  }

  private static final class KvServiceMethodDescriptorSupplier
      extends KvServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    KvServiceMethodDescriptorSupplier(String methodName) {
      this.methodName = methodName;
    }

    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (KvServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new KvServiceFileDescriptorSupplier())
              .addMethod(getBeginMethod())
              .addMethod(getCommitMethod())
              .addMethod(getAbortMethod())
              .addMethod(getRollbackMethod())
              .addMethod(getIsAliveMethod())
              .addMethod(getInsertMethod())
              .addMethod(getDeleteMethod())
              .addMethod(getGetMethod())
              .addMethod(getSetMethod())
              .build();
        }
      }
    }
    return result;
  }
}
