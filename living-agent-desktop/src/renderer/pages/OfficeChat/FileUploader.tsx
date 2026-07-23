/**
 * P1: 文件上传组件
 * 支持：拖拽上传 + 点击选择 + 多文件 + 预览 + 进度条 + 取消上传
 * 集成：OfficeChatPage 输入区域
 */

import { useState, useRef, useCallback, useEffect } from 'react';
import type { ChatAttachment } from './OfficeChatPage';

interface FileUploaderProps {
  backendUrl: string;
  onFilesSelected: (attachments: ChatAttachment[]) => void;
  onUploadStart?: (files: File[]) => void;
  onUploadProgress?: (fileName: string, progress: number) => void;
  onUploadComplete?: (attachments: ChatAttachment[]) => void;
  onUploadError?: (fileName: string, error: string) => void;
  maxFileSize?: number; // 单位：MB
  maxFiles?: number;
  accept?: string; // 如 'image/*,.pdf,.doc,.docx'
  disabled?: boolean;
}

interface UploadingFile {
  file: File;
  progress: number;
  status: 'uploading' | 'success' | 'error';
  error?: string;
  attachment?: ChatAttachment;
}

/**
 * 文件大小格式化
 */
function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

/**
 * 获取文件类型
 */
function getFileType(file: File): ChatAttachment['type'] {
  if (file.type.startsWith('image/')) return 'image';
  if (file.type.startsWith('audio/')) return 'audio';
  return 'file';
}

/**
 * FileUploader 组件
 */
export default function FileUploader({
  backendUrl,
  onFilesSelected,
  onUploadStart,
  onUploadProgress,
  onUploadComplete,
  onUploadError,
  maxFileSize = 50, // 默认 50MB
  maxFiles = 10,
  accept = 'image/*,audio/*,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.md,.json,.csv',
  disabled = false,
}: FileUploaderProps) {
  const [isDragging, setIsDragging] = useState(false);
  const [uploadingFiles, setUploadingFiles] = useState<UploadingFile[]>([]);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const dragCounterRef = useRef(0);

  // 验证文件
  const validateFiles = useCallback((files: File[]): { valid: File[]; errors: string[] } => {
    const valid: File[] = [];
    const errors: string[] = [];
    const maxSizeBytes = maxFileSize * 1024 * 1024;

    for (const file of files) {
      if (file.size > maxSizeBytes) {
        errors.push(`文件 "${file.name}" 超过 ${maxFileSize}MB 限制`);
        continue;
      }
      valid.push(file);
    }

    if (valid.length > maxFiles) {
      errors.push(`最多只能上传 ${maxFiles} 个文件，已选择 ${valid.length} 个`);
      return { valid: valid.slice(0, maxFiles), errors };
    }

    return { valid, errors };
  }, [maxFileSize, maxFiles]);

  // 模拟上传（实际项目中应调用后端 API）
  const simulateUpload = useCallback(async (file: File): Promise<ChatAttachment> => {
    // 模拟上传进度
    for (let progress = 0; progress <= 100; progress += 20) {
      await new Promise(resolve => setTimeout(resolve, 100));
      onUploadProgress?.(file.name, progress);
    }

    // 返回模拟的附件信息
    return {
      fileId: `file-${Date.now()}-${Math.random().toString(36).slice(2)}`,
      type: getFileType(file),
      name: file.name,
      size: file.size,
      url: URL.createObjectURL(file), // 本地预览 URL
      thumbnailUrl: file.type.startsWith('image/') ? URL.createObjectURL(file) : undefined,
    };
  }, [onUploadProgress]);

  // 处理文件上传
  const handleFiles = useCallback(async (files: File[]) => {
    if (disabled || files.length === 0) return;

    const { valid, errors } = validateFiles(files);
    if (errors.length > 0) {
      errors.forEach(err => onUploadError?.('validation', err));
    }
    if (valid.length === 0) return;

    // 初始化上传状态
    const initialUploading: UploadingFile[] = valid.map(file => ({
      file,
      progress: 0,
      status: 'uploading' as const,
    }));
    setUploadingFiles(initialUploading);
    onUploadStart?.(valid);

    // 上传每个文件
    const completedAttachments: ChatAttachment[] = [];
    for (let i = 0; i < valid.length; i++) {
      const file = valid[i];
      try {
        const attachment = await simulateUpload(file);
        completedAttachments.push(attachment);
        
        setUploadingFiles(prev => prev.map((uf, idx) => 
          idx === i ? { ...uf, progress: 100, status: 'success', attachment } : uf
        ));
      } catch (error) {
        const errorMsg = error instanceof Error ? error.message : '上传失败';
        onUploadError?.(file.name, errorMsg);
        setUploadingFiles(prev => prev.map((uf, idx) => 
          idx === i ? { ...uf, status: 'error', error: errorMsg } : uf
        ));
      }
    }

    // 回调完成
    if (completedAttachments.length > 0) {
      onUploadComplete?.(completedAttachments);
      onFilesSelected(completedAttachments);
    }

    // 延迟清除上传状态
    setTimeout(() => {
      setUploadingFiles([]);
    }, 2000);
  }, [disabled, validateFiles, simulateUpload, onUploadStart, onUploadError, onUploadComplete, onFilesSelected]);

  // 点击选择文件
  const handleButtonClick = useCallback(() => {
    fileInputRef.current?.click();
  }, []);

  const handleInputChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files ? Array.from(e.target.files) : [];
    handleFiles(files);
    // 清空 input 以便重复选择同一文件
    if (fileInputRef.current) fileInputRef.current.value = '';
  }, [handleFiles]);

  // 拖拽事件
  const handleDragEnter = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    dragCounterRef.current++;
    if (e.dataTransfer.items.length > 0) {
      setIsDragging(true);
    }
  }, []);

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    dragCounterRef.current--;
    if (dragCounterRef.current === 0) {
      setIsDragging(false);
    }
  }, []);

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
  }, []);

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
    dragCounterRef.current = 0;

    const files = e.dataTransfer.files ? Array.from(e.dataTransfer.files) : [];
    handleFiles(files);
  }, [handleFiles]);

  // 取消上传
  const handleCancelUpload = useCallback((index: number) => {
    setUploadingFiles(prev => prev.filter((_, idx) => idx !== index));
  }, []);

  return (
    <div className="file-uploader">
      {/* 隐藏的文件输入 */}
      <input
        ref={fileInputRef}
        type="file"
        multiple
        accept={accept}
        onChange={handleInputChange}
        style={{ display: 'none' }}
        disabled={disabled}
      />

      {/* 上传按钮 */}
      <button
        className="btn btn-icon file-uploader__btn"
        onClick={handleButtonClick}
        disabled={disabled}
        title="上传文件（图片、文档等）"
        aria-label="上传文件"
      >
        📎
      </button>

      {/* 拖拽区域（仅在拖拽时显示） */}
      {isDragging && (
        <div
          className="file-uploader__drop-zone"
          onDragEnter={handleDragEnter}
          onDragLeave={handleDragLeave}
          onDragOver={handleDragOver}
          onDrop={handleDrop}
        >
          <div className="file-uploader__drop-zone-content">
            <span className="file-uploader__drop-icon">📁</span>
            <span className="file-uploader__drop-text">拖拽文件到此处上传</span>
          </div>
        </div>
      )}

      {/* 上传进度列表 */}
      {uploadingFiles.length > 0 && (
        <div className="file-uploader__progress-list">
          {uploadingFiles.map((uf, idx) => (
            <div
              key={`${uf.file.name}-${idx}`}
              className={`file-uploader__progress-item file-uploader__progress-item--${uf.status}`}
            >
              <span className="file-uploader__progress-name">{uf.file.name}</span>
              {uf.status === 'uploading' && (
                <div className="file-uploader__progress-bar-container">
                  <div
                    className="file-uploader__progress-bar"
                    style={{ width: `${uf.progress}%` }}
                  />
                </div>
              )}
              {uf.status === 'success' && (
                <span className="file-uploader__progress-status">✅</span>
              )}
              {uf.status === 'error' && (
                <span className="file-uploader__progress-status file-uploader__progress-status--error">
                  ❌ {uf.error}
                </span>
              )}
              {uf.status === 'uploading' && (
                <button
                  className="file-uploader__cancel-btn"
                  onClick={() => handleCancelUpload(idx)}
                  title="取消上传"
                >
                  ✕
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/**
 * P1: 文件预览组件（用于显示待发送的附件）
 */
interface FilePreviewProps {
  attachments: ChatAttachment[];
  onRemove: (index: number) => void;
}

export function FilePreview({ attachments, onRemove }: FilePreviewProps) {
  if (attachments.length === 0) return null;

  return (
    <div className="file-preview">
      {attachments.map((att, idx) => (
        <div key={att.fileId} className="file-preview__item">
          {att.type === 'image' && att.thumbnailUrl ? (
            <img src={att.thumbnailUrl} alt={att.name} className="file-preview__thumbnail" />
          ) : (
            <div className="file-preview__icon-container">
              <span className="file-preview__icon">
                {att.type === 'audio' ? '🎵' : '📎'}
              </span>
            </div>
          )}
          <div className="file-preview__info">
            <span className="file-preview__name">{att.name}</span>
            {att.size && <span className="file-preview__size">{formatFileSize(att.size)}</span>}
          </div>
          <button
            className="file-preview__remove"
            onClick={() => onRemove(idx)}
            title="移除"
          >
            ✕
          </button>
        </div>
      ))}
    </div>
  );
}