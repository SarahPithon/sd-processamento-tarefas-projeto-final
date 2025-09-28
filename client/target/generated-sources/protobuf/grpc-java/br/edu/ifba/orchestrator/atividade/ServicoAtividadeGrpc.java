package br.edu.ifba.orchestrator.atividade;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.58.0)",
    comments = "Source: activity.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class ServicoAtividadeGrpc {

  private ServicoAtividadeGrpc() {}

  public static final java.lang.String SERVICE_NAME = "br.edu.ifba.atividade.ServicoAtividade";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.EnviarAtividadeRequisicao,
      br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.EnviarAtividadeResposta> getEnviarAtividadeMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "enviarAtividade",
      requestType = br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.EnviarAtividadeRequisicao.class,
      responseType = br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.EnviarAtividadeResposta.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.EnviarAtividadeRequisicao,
      br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.EnviarAtividadeResposta> getEnviarAtividadeMethod() {
    io.grpc.MethodDescriptor<br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.EnviarAtividadeRequisicao, br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.EnviarAtividadeResposta> getEnviarAtividadeMethod;
    if ((getEnviarAtividadeMethod = ServicoAtividadeGrpc.getEnviarAtividadeMethod) == null) {
      synchronized (ServicoAtividadeGrpc.class) {
        if ((getEnviarAtividadeMethod = ServicoAtividadeGrpc.getEnviarAtividadeMethod) == null) {
          ServicoAtividadeGrpc.getEnviarAtividadeMethod = getEnviarAtividadeMethod =
              io.grpc.MethodDescriptor.<br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.EnviarAtividadeRequisicao, br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.EnviarAtividadeResposta>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "enviarAtividade"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.EnviarAtividadeRequisicao.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.EnviarAtividadeResposta.getDefaultInstance()))
              .setSchemaDescriptor(new ServicoAtividadeMethodDescriptorSupplier("enviarAtividade"))
              .build();
        }
      }
    }
    return getEnviarAtividadeMethod;
  }

  private static volatile io.grpc.MethodDescriptor<br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.ListarAtividadesRequisicao,
      br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.ListarAtividadesResposta> getListarAtividadesMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "listarAtividades",
      requestType = br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.ListarAtividadesRequisicao.class,
      responseType = br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.ListarAtividadesResposta.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.ListarAtividadesRequisicao,
      br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.ListarAtividadesResposta> getListarAtividadesMethod() {
    io.grpc.MethodDescriptor<br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.ListarAtividadesRequisicao, br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.ListarAtividadesResposta> getListarAtividadesMethod;
    if ((getListarAtividadesMethod = ServicoAtividadeGrpc.getListarAtividadesMethod) == null) {
      synchronized (ServicoAtividadeGrpc.class) {
        if ((getListarAtividadesMethod = ServicoAtividadeGrpc.getListarAtividadesMethod) == null) {
          ServicoAtividadeGrpc.getListarAtividadesMethod = getListarAtividadesMethod =
              io.grpc.MethodDescriptor.<br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.ListarAtividadesRequisicao, br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.ListarAtividadesResposta>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "listarAtividades"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.ListarAtividadesRequisicao.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.ListarAtividadesResposta.getDefaultInstance()))
              .setSchemaDescriptor(new ServicoAtividadeMethodDescriptorSupplier("listarAtividades"))
              .build();
        }
      }
    }
    return getListarAtividadesMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ServicoAtividadeStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ServicoAtividadeStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ServicoAtividadeStub>() {
        @java.lang.Override
        public ServicoAtividadeStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ServicoAtividadeStub(channel, callOptions);
        }
      };
    return ServicoAtividadeStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ServicoAtividadeBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ServicoAtividadeBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ServicoAtividadeBlockingStub>() {
        @java.lang.Override
        public ServicoAtividadeBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ServicoAtividadeBlockingStub(channel, callOptions);
        }
      };
    return ServicoAtividadeBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ServicoAtividadeFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ServicoAtividadeFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ServicoAtividadeFutureStub>() {
        @java.lang.Override
        public ServicoAtividadeFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ServicoAtividadeFutureStub(channel, callOptions);
        }
      };
    return ServicoAtividadeFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void enviarAtividade(br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.EnviarAtividadeRequisicao request,
        io.grpc.stub.StreamObserver<br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.EnviarAtividadeResposta> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getEnviarAtividadeMethod(), responseObserver);
    }

    /**
     */
    default void listarAtividades(br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.ListarAtividadesRequisicao request,
        io.grpc.stub.StreamObserver<br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.ListarAtividadesResposta> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getListarAtividadesMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service ServicoAtividade.
   */
  public static abstract class ServicoAtividadeImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return ServicoAtividadeGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service ServicoAtividade.
   */
  public static final class ServicoAtividadeStub
      extends io.grpc.stub.AbstractAsyncStub<ServicoAtividadeStub> {
    private ServicoAtividadeStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ServicoAtividadeStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ServicoAtividadeStub(channel, callOptions);
    }

    /**
     */
    public void enviarAtividade(br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.EnviarAtividadeRequisicao request,
        io.grpc.stub.StreamObserver<br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.EnviarAtividadeResposta> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getEnviarAtividadeMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void listarAtividades(br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.ListarAtividadesRequisicao request,
        io.grpc.stub.StreamObserver<br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.ListarAtividadesResposta> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getListarAtividadesMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service ServicoAtividade.
   */
  public static final class ServicoAtividadeBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<ServicoAtividadeBlockingStub> {
    private ServicoAtividadeBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ServicoAtividadeBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ServicoAtividadeBlockingStub(channel, callOptions);
    }

    /**
     */
    public br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.EnviarAtividadeResposta enviarAtividade(br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.EnviarAtividadeRequisicao request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getEnviarAtividadeMethod(), getCallOptions(), request);
    }

    /**
     */
    public br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.ListarAtividadesResposta listarAtividades(br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.ListarAtividadesRequisicao request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getListarAtividadesMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service ServicoAtividade.
   */
  public static final class ServicoAtividadeFutureStub
      extends io.grpc.stub.AbstractFutureStub<ServicoAtividadeFutureStub> {
    private ServicoAtividadeFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ServicoAtividadeFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ServicoAtividadeFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.EnviarAtividadeResposta> enviarAtividade(
        br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.EnviarAtividadeRequisicao request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getEnviarAtividadeMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.ListarAtividadesResposta> listarAtividades(
        br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.ListarAtividadesRequisicao request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getListarAtividadesMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_ENVIAR_ATIVIDADE = 0;
  private static final int METHODID_LISTAR_ATIVIDADES = 1;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_ENVIAR_ATIVIDADE:
          serviceImpl.enviarAtividade((br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.EnviarAtividadeRequisicao) request,
              (io.grpc.stub.StreamObserver<br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.EnviarAtividadeResposta>) responseObserver);
          break;
        case METHODID_LISTAR_ATIVIDADES:
          serviceImpl.listarAtividades((br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.ListarAtividadesRequisicao) request,
              (io.grpc.stub.StreamObserver<br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.ListarAtividadesResposta>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getEnviarAtividadeMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.EnviarAtividadeRequisicao,
              br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.EnviarAtividadeResposta>(
                service, METHODID_ENVIAR_ATIVIDADE)))
        .addMethod(
          getListarAtividadesMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.ListarAtividadesRequisicao,
              br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.ListarAtividadesResposta>(
                service, METHODID_LISTAR_ATIVIDADES)))
        .build();
  }

  private static abstract class ServicoAtividadeBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ServicoAtividadeBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("ServicoAtividade");
    }
  }

  private static final class ServicoAtividadeFileDescriptorSupplier
      extends ServicoAtividadeBaseDescriptorSupplier {
    ServicoAtividadeFileDescriptorSupplier() {}
  }

  private static final class ServicoAtividadeMethodDescriptorSupplier
      extends ServicoAtividadeBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    ServicoAtividadeMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (ServicoAtividadeGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ServicoAtividadeFileDescriptorSupplier())
              .addMethod(getEnviarAtividadeMethod())
              .addMethod(getListarAtividadesMethod())
              .build();
        }
      }
    }
    return result;
  }
}
