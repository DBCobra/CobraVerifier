// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: txn.proto

package kvClient.pb;

/**
 * Protobuf type {@code pb.ResponseKv}
 */
public  final class ResponseKv extends
    com.google.protobuf.GeneratedMessageV3 implements
    // @@protoc_insertion_point(message_implements:pb.ResponseKv)
    ResponseKvOrBuilder {
private static final long serialVersionUID = 0L;
  // Use ResponseKv.newBuilder() to construct.
  private ResponseKv(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
    super(builder);
  }
  private ResponseKv() {
    txnid_ = 0L;
  }

  @java.lang.Override
  public final com.google.protobuf.UnknownFieldSet
  getUnknownFields() {
    return this.unknownFields;
  }
  private ResponseKv(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    this();
    if (extensionRegistry == null) {
      throw new java.lang.NullPointerException();
    }
    int mutable_bitField0_ = 0;
    com.google.protobuf.UnknownFieldSet.Builder unknownFields =
        com.google.protobuf.UnknownFieldSet.newBuilder();
    try {
      boolean done = false;
      while (!done) {
        int tag = input.readTag();
        switch (tag) {
          case 0:
            done = true;
            break;
          default: {
            if (!parseUnknownFieldProto3(
                input, unknownFields, extensionRegistry, tag)) {
              done = true;
            }
            break;
          }
          case 8: {

            txnid_ = input.readInt64();
            break;
          }
          case 18: {
            kvClient.pb.KeyValue.Builder subBuilder = null;
            if (resultCase_ == 2) {
              subBuilder = ((kvClient.pb.KeyValue) result_).toBuilder();
            }
            result_ =
                input.readMessage(kvClient.pb.KeyValue.parser(), extensionRegistry);
            if (subBuilder != null) {
              subBuilder.mergeFrom((kvClient.pb.KeyValue) result_);
              result_ = subBuilder.buildPartial();
            }
            resultCase_ = 2;
            break;
          }
          case 26: {
            kvClient.pb.Key.Builder subBuilder = null;
            if (resultCase_ == 3) {
              subBuilder = ((kvClient.pb.Key) result_).toBuilder();
            }
            result_ =
                input.readMessage(kvClient.pb.Key.parser(), extensionRegistry);
            if (subBuilder != null) {
              subBuilder.mergeFrom((kvClient.pb.Key) result_);
              result_ = subBuilder.buildPartial();
            }
            resultCase_ = 3;
            break;
          }
          case 34: {
            kvClient.pb.ServiceError.Builder subBuilder = null;
            if (resultCase_ == 4) {
              subBuilder = ((kvClient.pb.ServiceError) result_).toBuilder();
            }
            result_ =
                input.readMessage(kvClient.pb.ServiceError.parser(), extensionRegistry);
            if (subBuilder != null) {
              subBuilder.mergeFrom((kvClient.pb.ServiceError) result_);
              result_ = subBuilder.buildPartial();
            }
            resultCase_ = 4;
            break;
          }
        }
      }
    } catch (com.google.protobuf.InvalidProtocolBufferException e) {
      throw e.setUnfinishedMessage(this);
    } catch (java.io.IOException e) {
      throw new com.google.protobuf.InvalidProtocolBufferException(
          e).setUnfinishedMessage(this);
    } finally {
      this.unknownFields = unknownFields.build();
      makeExtensionsImmutable();
    }
  }
  public static final com.google.protobuf.Descriptors.Descriptor
      getDescriptor() {
    return kvClient.pb.TxnPB.internal_static_pb_ResponseKv_descriptor;
  }

  protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return kvClient.pb.TxnPB.internal_static_pb_ResponseKv_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            kvClient.pb.ResponseKv.class, kvClient.pb.ResponseKv.Builder.class);
  }

  private int resultCase_ = 0;
  private java.lang.Object result_;
  public enum ResultCase
      implements com.google.protobuf.Internal.EnumLite {
    KV(2),
    K(3),
    E(4),
    RESULT_NOT_SET(0);
    private final int value;
    private ResultCase(int value) {
      this.value = value;
    }
    /**
     * @deprecated Use {@link #forNumber(int)} instead.
     */
    @java.lang.Deprecated
    public static ResultCase valueOf(int value) {
      return forNumber(value);
    }

    public static ResultCase forNumber(int value) {
      switch (value) {
        case 2: return KV;
        case 3: return K;
        case 4: return E;
        case 0: return RESULT_NOT_SET;
        default: return null;
      }
    }
    public int getNumber() {
      return this.value;
    }
  };

  public ResultCase
  getResultCase() {
    return ResultCase.forNumber(
        resultCase_);
  }

  public static final int TXNID_FIELD_NUMBER = 1;
  private long txnid_;
  /**
   * <code>int64 txnid = 1;</code>
   */
  public long getTxnid() {
    return txnid_;
  }

  public static final int KV_FIELD_NUMBER = 2;
  /**
   * <code>.pb.KeyValue kv = 2;</code>
   */
  public boolean hasKv() {
    return resultCase_ == 2;
  }
  /**
   * <code>.pb.KeyValue kv = 2;</code>
   */
  public kvClient.pb.KeyValue getKv() {
    if (resultCase_ == 2) {
       return (kvClient.pb.KeyValue) result_;
    }
    return kvClient.pb.KeyValue.getDefaultInstance();
  }
  /**
   * <code>.pb.KeyValue kv = 2;</code>
   */
  public kvClient.pb.KeyValueOrBuilder getKvOrBuilder() {
    if (resultCase_ == 2) {
       return (kvClient.pb.KeyValue) result_;
    }
    return kvClient.pb.KeyValue.getDefaultInstance();
  }

  public static final int K_FIELD_NUMBER = 3;
  /**
   * <code>.pb.Key k = 3;</code>
   */
  public boolean hasK() {
    return resultCase_ == 3;
  }
  /**
   * <code>.pb.Key k = 3;</code>
   */
  public kvClient.pb.Key getK() {
    if (resultCase_ == 3) {
       return (kvClient.pb.Key) result_;
    }
    return kvClient.pb.Key.getDefaultInstance();
  }
  /**
   * <code>.pb.Key k = 3;</code>
   */
  public kvClient.pb.KeyOrBuilder getKOrBuilder() {
    if (resultCase_ == 3) {
       return (kvClient.pb.Key) result_;
    }
    return kvClient.pb.Key.getDefaultInstance();
  }

  public static final int E_FIELD_NUMBER = 4;
  /**
   * <code>.pb.ServiceError e = 4;</code>
   */
  public boolean hasE() {
    return resultCase_ == 4;
  }
  /**
   * <code>.pb.ServiceError e = 4;</code>
   */
  public kvClient.pb.ServiceError getE() {
    if (resultCase_ == 4) {
       return (kvClient.pb.ServiceError) result_;
    }
    return kvClient.pb.ServiceError.getDefaultInstance();
  }
  /**
   * <code>.pb.ServiceError e = 4;</code>
   */
  public kvClient.pb.ServiceErrorOrBuilder getEOrBuilder() {
    if (resultCase_ == 4) {
       return (kvClient.pb.ServiceError) result_;
    }
    return kvClient.pb.ServiceError.getDefaultInstance();
  }

  private byte memoizedIsInitialized = -1;
  public final boolean isInitialized() {
    byte isInitialized = memoizedIsInitialized;
    if (isInitialized == 1) return true;
    if (isInitialized == 0) return false;

    memoizedIsInitialized = 1;
    return true;
  }

  public void writeTo(com.google.protobuf.CodedOutputStream output)
                      throws java.io.IOException {
    if (txnid_ != 0L) {
      output.writeInt64(1, txnid_);
    }
    if (resultCase_ == 2) {
      output.writeMessage(2, (kvClient.pb.KeyValue) result_);
    }
    if (resultCase_ == 3) {
      output.writeMessage(3, (kvClient.pb.Key) result_);
    }
    if (resultCase_ == 4) {
      output.writeMessage(4, (kvClient.pb.ServiceError) result_);
    }
    unknownFields.writeTo(output);
  }

  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1) return size;

    size = 0;
    if (txnid_ != 0L) {
      size += com.google.protobuf.CodedOutputStream
        .computeInt64Size(1, txnid_);
    }
    if (resultCase_ == 2) {
      size += com.google.protobuf.CodedOutputStream
        .computeMessageSize(2, (kvClient.pb.KeyValue) result_);
    }
    if (resultCase_ == 3) {
      size += com.google.protobuf.CodedOutputStream
        .computeMessageSize(3, (kvClient.pb.Key) result_);
    }
    if (resultCase_ == 4) {
      size += com.google.protobuf.CodedOutputStream
        .computeMessageSize(4, (kvClient.pb.ServiceError) result_);
    }
    size += unknownFields.getSerializedSize();
    memoizedSize = size;
    return size;
  }

  @java.lang.Override
  public boolean equals(final java.lang.Object obj) {
    if (obj == this) {
     return true;
    }
    if (!(obj instanceof kvClient.pb.ResponseKv)) {
      return super.equals(obj);
    }
    kvClient.pb.ResponseKv other = (kvClient.pb.ResponseKv) obj;

    boolean result = true;
    result = result && (getTxnid()
        == other.getTxnid());
    result = result && getResultCase().equals(
        other.getResultCase());
    if (!result) return false;
    switch (resultCase_) {
      case 2:
        result = result && getKv()
            .equals(other.getKv());
        break;
      case 3:
        result = result && getK()
            .equals(other.getK());
        break;
      case 4:
        result = result && getE()
            .equals(other.getE());
        break;
      case 0:
      default:
    }
    result = result && unknownFields.equals(other.unknownFields);
    return result;
  }

  @java.lang.Override
  public int hashCode() {
    if (memoizedHashCode != 0) {
      return memoizedHashCode;
    }
    int hash = 41;
    hash = (19 * hash) + getDescriptor().hashCode();
    hash = (37 * hash) + TXNID_FIELD_NUMBER;
    hash = (53 * hash) + com.google.protobuf.Internal.hashLong(
        getTxnid());
    switch (resultCase_) {
      case 2:
        hash = (37 * hash) + KV_FIELD_NUMBER;
        hash = (53 * hash) + getKv().hashCode();
        break;
      case 3:
        hash = (37 * hash) + K_FIELD_NUMBER;
        hash = (53 * hash) + getK().hashCode();
        break;
      case 4:
        hash = (37 * hash) + E_FIELD_NUMBER;
        hash = (53 * hash) + getE().hashCode();
        break;
      case 0:
      default:
    }
    hash = (29 * hash) + unknownFields.hashCode();
    memoizedHashCode = hash;
    return hash;
  }

  public static kvClient.pb.ResponseKv parseFrom(
      java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static kvClient.pb.ResponseKv parseFrom(
      java.nio.ByteBuffer data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static kvClient.pb.ResponseKv parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static kvClient.pb.ResponseKv parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static kvClient.pb.ResponseKv parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static kvClient.pb.ResponseKv parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static kvClient.pb.ResponseKv parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static kvClient.pb.ResponseKv parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }
  public static kvClient.pb.ResponseKv parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input);
  }
  public static kvClient.pb.ResponseKv parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
  }
  public static kvClient.pb.ResponseKv parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static kvClient.pb.ResponseKv parseFrom(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }

  public Builder newBuilderForType() { return newBuilder(); }
  public static Builder newBuilder() {
    return DEFAULT_INSTANCE.toBuilder();
  }
  public static Builder newBuilder(kvClient.pb.ResponseKv prototype) {
    return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
  }
  public Builder toBuilder() {
    return this == DEFAULT_INSTANCE
        ? new Builder() : new Builder().mergeFrom(this);
  }

  @java.lang.Override
  protected Builder newBuilderForType(
      com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
    Builder builder = new Builder(parent);
    return builder;
  }
  /**
   * Protobuf type {@code pb.ResponseKv}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:pb.ResponseKv)
      kvClient.pb.ResponseKvOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return kvClient.pb.TxnPB.internal_static_pb_ResponseKv_descriptor;
    }

    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return kvClient.pb.TxnPB.internal_static_pb_ResponseKv_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              kvClient.pb.ResponseKv.class, kvClient.pb.ResponseKv.Builder.class);
    }

    // Construct using pb.ResponseKv.newBuilder()
    private Builder() {
      maybeForceBuilderInitialization();
    }

    private Builder(
        com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
      super(parent);
      maybeForceBuilderInitialization();
    }
    private void maybeForceBuilderInitialization() {
      if (com.google.protobuf.GeneratedMessageV3
              .alwaysUseFieldBuilders) {
      }
    }
    public Builder clear() {
      super.clear();
      txnid_ = 0L;

      resultCase_ = 0;
      result_ = null;
      return this;
    }

    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return kvClient.pb.TxnPB.internal_static_pb_ResponseKv_descriptor;
    }

    public kvClient.pb.ResponseKv getDefaultInstanceForType() {
      return kvClient.pb.ResponseKv.getDefaultInstance();
    }

    public kvClient.pb.ResponseKv build() {
      kvClient.pb.ResponseKv result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    public kvClient.pb.ResponseKv buildPartial() {
      kvClient.pb.ResponseKv result = new kvClient.pb.ResponseKv(this);
      result.txnid_ = txnid_;
      if (resultCase_ == 2) {
        if (kvBuilder_ == null) {
          result.result_ = result_;
        } else {
          result.result_ = kvBuilder_.build();
        }
      }
      if (resultCase_ == 3) {
        if (kBuilder_ == null) {
          result.result_ = result_;
        } else {
          result.result_ = kBuilder_.build();
        }
      }
      if (resultCase_ == 4) {
        if (eBuilder_ == null) {
          result.result_ = result_;
        } else {
          result.result_ = eBuilder_.build();
        }
      }
      result.resultCase_ = resultCase_;
      onBuilt();
      return result;
    }

    public Builder clone() {
      return (Builder) super.clone();
    }
    public Builder setField(
        com.google.protobuf.Descriptors.FieldDescriptor field,
        java.lang.Object value) {
      return (Builder) super.setField(field, value);
    }
    public Builder clearField(
        com.google.protobuf.Descriptors.FieldDescriptor field) {
      return (Builder) super.clearField(field);
    }
    public Builder clearOneof(
        com.google.protobuf.Descriptors.OneofDescriptor oneof) {
      return (Builder) super.clearOneof(oneof);
    }
    public Builder setRepeatedField(
        com.google.protobuf.Descriptors.FieldDescriptor field,
        int index, java.lang.Object value) {
      return (Builder) super.setRepeatedField(field, index, value);
    }
    public Builder addRepeatedField(
        com.google.protobuf.Descriptors.FieldDescriptor field,
        java.lang.Object value) {
      return (Builder) super.addRepeatedField(field, value);
    }
    public Builder mergeFrom(com.google.protobuf.Message other) {
      if (other instanceof kvClient.pb.ResponseKv) {
        return mergeFrom((kvClient.pb.ResponseKv)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(kvClient.pb.ResponseKv other) {
      if (other == kvClient.pb.ResponseKv.getDefaultInstance()) return this;
      if (other.getTxnid() != 0L) {
        setTxnid(other.getTxnid());
      }
      switch (other.getResultCase()) {
        case KV: {
          mergeKv(other.getKv());
          break;
        }
        case K: {
          mergeK(other.getK());
          break;
        }
        case E: {
          mergeE(other.getE());
          break;
        }
        case RESULT_NOT_SET: {
          break;
        }
      }
      this.mergeUnknownFields(other.unknownFields);
      onChanged();
      return this;
    }

    public final boolean isInitialized() {
      return true;
    }

    public Builder mergeFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      kvClient.pb.ResponseKv parsedMessage = null;
      try {
        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        parsedMessage = (kvClient.pb.ResponseKv) e.getUnfinishedMessage();
        throw e.unwrapIOException();
      } finally {
        if (parsedMessage != null) {
          mergeFrom(parsedMessage);
        }
      }
      return this;
    }
    private int resultCase_ = 0;
    private java.lang.Object result_;
    public ResultCase
        getResultCase() {
      return ResultCase.forNumber(
          resultCase_);
    }

    public Builder clearResult() {
      resultCase_ = 0;
      result_ = null;
      onChanged();
      return this;
    }


    private long txnid_ ;
    /**
     * <code>int64 txnid = 1;</code>
     */
    public long getTxnid() {
      return txnid_;
    }
    /**
     * <code>int64 txnid = 1;</code>
     */
    public Builder setTxnid(long value) {
      
      txnid_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>int64 txnid = 1;</code>
     */
    public Builder clearTxnid() {
      
      txnid_ = 0L;
      onChanged();
      return this;
    }

    private com.google.protobuf.SingleFieldBuilderV3<
        kvClient.pb.KeyValue, kvClient.pb.KeyValue.Builder, kvClient.pb.KeyValueOrBuilder> kvBuilder_;
    /**
     * <code>.pb.KeyValue kv = 2;</code>
     */
    public boolean hasKv() {
      return resultCase_ == 2;
    }
    /**
     * <code>.pb.KeyValue kv = 2;</code>
     */
    public kvClient.pb.KeyValue getKv() {
      if (kvBuilder_ == null) {
        if (resultCase_ == 2) {
          return (kvClient.pb.KeyValue) result_;
        }
        return kvClient.pb.KeyValue.getDefaultInstance();
      } else {
        if (resultCase_ == 2) {
          return kvBuilder_.getMessage();
        }
        return kvClient.pb.KeyValue.getDefaultInstance();
      }
    }
    /**
     * <code>.pb.KeyValue kv = 2;</code>
     */
    public Builder setKv(kvClient.pb.KeyValue value) {
      if (kvBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        result_ = value;
        onChanged();
      } else {
        kvBuilder_.setMessage(value);
      }
      resultCase_ = 2;
      return this;
    }
    /**
     * <code>.pb.KeyValue kv = 2;</code>
     */
    public Builder setKv(
        kvClient.pb.KeyValue.Builder builderForValue) {
      if (kvBuilder_ == null) {
        result_ = builderForValue.build();
        onChanged();
      } else {
        kvBuilder_.setMessage(builderForValue.build());
      }
      resultCase_ = 2;
      return this;
    }
    /**
     * <code>.pb.KeyValue kv = 2;</code>
     */
    public Builder mergeKv(kvClient.pb.KeyValue value) {
      if (kvBuilder_ == null) {
        if (resultCase_ == 2 &&
            result_ != kvClient.pb.KeyValue.getDefaultInstance()) {
          result_ = kvClient.pb.KeyValue.newBuilder((kvClient.pb.KeyValue) result_)
              .mergeFrom(value).buildPartial();
        } else {
          result_ = value;
        }
        onChanged();
      } else {
        if (resultCase_ == 2) {
          kvBuilder_.mergeFrom(value);
        }
        kvBuilder_.setMessage(value);
      }
      resultCase_ = 2;
      return this;
    }
    /**
     * <code>.pb.KeyValue kv = 2;</code>
     */
    public Builder clearKv() {
      if (kvBuilder_ == null) {
        if (resultCase_ == 2) {
          resultCase_ = 0;
          result_ = null;
          onChanged();
        }
      } else {
        if (resultCase_ == 2) {
          resultCase_ = 0;
          result_ = null;
        }
        kvBuilder_.clear();
      }
      return this;
    }
    /**
     * <code>.pb.KeyValue kv = 2;</code>
     */
    public kvClient.pb.KeyValue.Builder getKvBuilder() {
      return getKvFieldBuilder().getBuilder();
    }
    /**
     * <code>.pb.KeyValue kv = 2;</code>
     */
    public kvClient.pb.KeyValueOrBuilder getKvOrBuilder() {
      if ((resultCase_ == 2) && (kvBuilder_ != null)) {
        return kvBuilder_.getMessageOrBuilder();
      } else {
        if (resultCase_ == 2) {
          return (kvClient.pb.KeyValue) result_;
        }
        return kvClient.pb.KeyValue.getDefaultInstance();
      }
    }
    /**
     * <code>.pb.KeyValue kv = 2;</code>
     */
    private com.google.protobuf.SingleFieldBuilderV3<
        kvClient.pb.KeyValue, kvClient.pb.KeyValue.Builder, kvClient.pb.KeyValueOrBuilder> 
        getKvFieldBuilder() {
      if (kvBuilder_ == null) {
        if (!(resultCase_ == 2)) {
          result_ = kvClient.pb.KeyValue.getDefaultInstance();
        }
        kvBuilder_ = new com.google.protobuf.SingleFieldBuilderV3<
            kvClient.pb.KeyValue, kvClient.pb.KeyValue.Builder, kvClient.pb.KeyValueOrBuilder>(
                (kvClient.pb.KeyValue) result_,
                getParentForChildren(),
                isClean());
        result_ = null;
      }
      resultCase_ = 2;
      onChanged();;
      return kvBuilder_;
    }

    private com.google.protobuf.SingleFieldBuilderV3<
        kvClient.pb.Key, kvClient.pb.Key.Builder, kvClient.pb.KeyOrBuilder> kBuilder_;
    /**
     * <code>.pb.Key k = 3;</code>
     */
    public boolean hasK() {
      return resultCase_ == 3;
    }
    /**
     * <code>.pb.Key k = 3;</code>
     */
    public kvClient.pb.Key getK() {
      if (kBuilder_ == null) {
        if (resultCase_ == 3) {
          return (kvClient.pb.Key) result_;
        }
        return kvClient.pb.Key.getDefaultInstance();
      } else {
        if (resultCase_ == 3) {
          return kBuilder_.getMessage();
        }
        return kvClient.pb.Key.getDefaultInstance();
      }
    }
    /**
     * <code>.pb.Key k = 3;</code>
     */
    public Builder setK(kvClient.pb.Key value) {
      if (kBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        result_ = value;
        onChanged();
      } else {
        kBuilder_.setMessage(value);
      }
      resultCase_ = 3;
      return this;
    }
    /**
     * <code>.pb.Key k = 3;</code>
     */
    public Builder setK(
        kvClient.pb.Key.Builder builderForValue) {
      if (kBuilder_ == null) {
        result_ = builderForValue.build();
        onChanged();
      } else {
        kBuilder_.setMessage(builderForValue.build());
      }
      resultCase_ = 3;
      return this;
    }
    /**
     * <code>.pb.Key k = 3;</code>
     */
    public Builder mergeK(kvClient.pb.Key value) {
      if (kBuilder_ == null) {
        if (resultCase_ == 3 &&
            result_ != kvClient.pb.Key.getDefaultInstance()) {
          result_ = kvClient.pb.Key.newBuilder((kvClient.pb.Key) result_)
              .mergeFrom(value).buildPartial();
        } else {
          result_ = value;
        }
        onChanged();
      } else {
        if (resultCase_ == 3) {
          kBuilder_.mergeFrom(value);
        }
        kBuilder_.setMessage(value);
      }
      resultCase_ = 3;
      return this;
    }
    /**
     * <code>.pb.Key k = 3;</code>
     */
    public Builder clearK() {
      if (kBuilder_ == null) {
        if (resultCase_ == 3) {
          resultCase_ = 0;
          result_ = null;
          onChanged();
        }
      } else {
        if (resultCase_ == 3) {
          resultCase_ = 0;
          result_ = null;
        }
        kBuilder_.clear();
      }
      return this;
    }
    /**
     * <code>.pb.Key k = 3;</code>
     */
    public kvClient.pb.Key.Builder getKBuilder() {
      return getKFieldBuilder().getBuilder();
    }
    /**
     * <code>.pb.Key k = 3;</code>
     */
    public kvClient.pb.KeyOrBuilder getKOrBuilder() {
      if ((resultCase_ == 3) && (kBuilder_ != null)) {
        return kBuilder_.getMessageOrBuilder();
      } else {
        if (resultCase_ == 3) {
          return (kvClient.pb.Key) result_;
        }
        return kvClient.pb.Key.getDefaultInstance();
      }
    }
    /**
     * <code>.pb.Key k = 3;</code>
     */
    private com.google.protobuf.SingleFieldBuilderV3<
        kvClient.pb.Key, kvClient.pb.Key.Builder, kvClient.pb.KeyOrBuilder> 
        getKFieldBuilder() {
      if (kBuilder_ == null) {
        if (!(resultCase_ == 3)) {
          result_ = kvClient.pb.Key.getDefaultInstance();
        }
        kBuilder_ = new com.google.protobuf.SingleFieldBuilderV3<
            kvClient.pb.Key, kvClient.pb.Key.Builder, kvClient.pb.KeyOrBuilder>(
                (kvClient.pb.Key) result_,
                getParentForChildren(),
                isClean());
        result_ = null;
      }
      resultCase_ = 3;
      onChanged();;
      return kBuilder_;
    }

    private com.google.protobuf.SingleFieldBuilderV3<
        kvClient.pb.ServiceError, kvClient.pb.ServiceError.Builder, kvClient.pb.ServiceErrorOrBuilder> eBuilder_;
    /**
     * <code>.pb.ServiceError e = 4;</code>
     */
    public boolean hasE() {
      return resultCase_ == 4;
    }
    /**
     * <code>.pb.ServiceError e = 4;</code>
     */
    public kvClient.pb.ServiceError getE() {
      if (eBuilder_ == null) {
        if (resultCase_ == 4) {
          return (kvClient.pb.ServiceError) result_;
        }
        return kvClient.pb.ServiceError.getDefaultInstance();
      } else {
        if (resultCase_ == 4) {
          return eBuilder_.getMessage();
        }
        return kvClient.pb.ServiceError.getDefaultInstance();
      }
    }
    /**
     * <code>.pb.ServiceError e = 4;</code>
     */
    public Builder setE(kvClient.pb.ServiceError value) {
      if (eBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        result_ = value;
        onChanged();
      } else {
        eBuilder_.setMessage(value);
      }
      resultCase_ = 4;
      return this;
    }
    /**
     * <code>.pb.ServiceError e = 4;</code>
     */
    public Builder setE(
        kvClient.pb.ServiceError.Builder builderForValue) {
      if (eBuilder_ == null) {
        result_ = builderForValue.build();
        onChanged();
      } else {
        eBuilder_.setMessage(builderForValue.build());
      }
      resultCase_ = 4;
      return this;
    }
    /**
     * <code>.pb.ServiceError e = 4;</code>
     */
    public Builder mergeE(kvClient.pb.ServiceError value) {
      if (eBuilder_ == null) {
        if (resultCase_ == 4 &&
            result_ != kvClient.pb.ServiceError.getDefaultInstance()) {
          result_ = kvClient.pb.ServiceError.newBuilder((kvClient.pb.ServiceError) result_)
              .mergeFrom(value).buildPartial();
        } else {
          result_ = value;
        }
        onChanged();
      } else {
        if (resultCase_ == 4) {
          eBuilder_.mergeFrom(value);
        }
        eBuilder_.setMessage(value);
      }
      resultCase_ = 4;
      return this;
    }
    /**
     * <code>.pb.ServiceError e = 4;</code>
     */
    public Builder clearE() {
      if (eBuilder_ == null) {
        if (resultCase_ == 4) {
          resultCase_ = 0;
          result_ = null;
          onChanged();
        }
      } else {
        if (resultCase_ == 4) {
          resultCase_ = 0;
          result_ = null;
        }
        eBuilder_.clear();
      }
      return this;
    }
    /**
     * <code>.pb.ServiceError e = 4;</code>
     */
    public kvClient.pb.ServiceError.Builder getEBuilder() {
      return getEFieldBuilder().getBuilder();
    }
    /**
     * <code>.pb.ServiceError e = 4;</code>
     */
    public kvClient.pb.ServiceErrorOrBuilder getEOrBuilder() {
      if ((resultCase_ == 4) && (eBuilder_ != null)) {
        return eBuilder_.getMessageOrBuilder();
      } else {
        if (resultCase_ == 4) {
          return (kvClient.pb.ServiceError) result_;
        }
        return kvClient.pb.ServiceError.getDefaultInstance();
      }
    }
    /**
     * <code>.pb.ServiceError e = 4;</code>
     */
    private com.google.protobuf.SingleFieldBuilderV3<
        kvClient.pb.ServiceError, kvClient.pb.ServiceError.Builder, kvClient.pb.ServiceErrorOrBuilder> 
        getEFieldBuilder() {
      if (eBuilder_ == null) {
        if (!(resultCase_ == 4)) {
          result_ = kvClient.pb.ServiceError.getDefaultInstance();
        }
        eBuilder_ = new com.google.protobuf.SingleFieldBuilderV3<
            kvClient.pb.ServiceError, kvClient.pb.ServiceError.Builder, kvClient.pb.ServiceErrorOrBuilder>(
                (kvClient.pb.ServiceError) result_,
                getParentForChildren(),
                isClean());
        result_ = null;
      }
      resultCase_ = 4;
      onChanged();;
      return eBuilder_;
    }
    public final Builder setUnknownFields(
        final com.google.protobuf.UnknownFieldSet unknownFields) {
      return super.setUnknownFieldsProto3(unknownFields);
    }

    public final Builder mergeUnknownFields(
        final com.google.protobuf.UnknownFieldSet unknownFields) {
      return super.mergeUnknownFields(unknownFields);
    }


    // @@protoc_insertion_point(builder_scope:pb.ResponseKv)
  }

  // @@protoc_insertion_point(class_scope:pb.ResponseKv)
  private static final kvClient.pb.ResponseKv DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new kvClient.pb.ResponseKv();
  }

  public static kvClient.pb.ResponseKv getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static final com.google.protobuf.Parser<ResponseKv>
      PARSER = new com.google.protobuf.AbstractParser<ResponseKv>() {
    public ResponseKv parsePartialFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return new ResponseKv(input, extensionRegistry);
    }
  };

  public static com.google.protobuf.Parser<ResponseKv> parser() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.protobuf.Parser<ResponseKv> getParserForType() {
    return PARSER;
  }

  public kvClient.pb.ResponseKv getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }

}

