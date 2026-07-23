/**
 * P2 截图标注编辑器组件
 *
 * 功能：
 * - 显示截图
 * - 提供标注工具：矩形框、箭头、文字、马赛克、高亮
 * - 标注完成插入到聊天输入框
 *
 * 工具说明：
 * - 矩形框：拖拽绘制矩形边框
 * - 箭头：拖拽绘制箭头线
 * - 文字：点击添加文字标注
 * - 马赛克：拖拽涂抹马赛克
 * - 高亮：拖拽绘制高亮线
 */
import { useState, useRef, useEffect, useCallback } from 'react';
import './ScreenshotEditor.css';

type Tool = 'rect' | 'arrow' | 'text' | 'mosaic' | 'highlight' | 'none';

interface Annotation {
  type: Tool;
  x: number;
  y: number;
  width?: number;
  height?: number;
  x2?: number;
  y2?: number;
  text?: string;
  color: string;
}

interface ScreenshotEditorProps {
  imageDataUrl: string;
  onInsert: (annotatedDataUrl: string) => void;
  onCancel: () => void;
}

export default function ScreenshotEditor({
  imageDataUrl,
  onInsert,
  onCancel
}: ScreenshotEditorProps) {
  const [currentTool, setCurrentTool] = useState<Tool>('rect');
  const [annotations, setAnnotations] = useState<Annotation[]>([]);
  const [isDrawing, setIsDrawing] = useState(false);
  const [startPos, setStartPos] = useState({ x: 0, y: 0 });
  const [currentAnnotation, setCurrentAnnotation] = useState<Annotation | null>(null);
  const [text, setText] = useState('');

  const canvasRef = useRef<HTMLCanvasElement>(null);
  const imageRef = useRef<HTMLImageElement | null>(null);

  // 工具颜色配置
  const TOOL_COLORS: Record<Tool, string> = {
    rect: '#ff4d4f',
    arrow: '#1890ff',
    text: '#52c41a',
    mosaic: '#000000',
    highlight: '#faad14',
    none: '#000000'
  };

  // 加载图片
  useEffect(() => {
    const img = new Image();
    img.onload = () => {
      imageRef.current = img;
      drawCanvas();
    };
    img.src = imageDataUrl;
  }, [imageDataUrl]);

  // 绘制 Canvas
  const drawCanvas = useCallback(() => {
    const canvas = canvasRef.current;
    const img = imageRef.current;
    if (!canvas || !img) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    // 设置 Canvas 尺寸
    canvas.width = img.width;
    canvas.height = img.height;

    // 绘制原始图片
    ctx.drawImage(img, 0, 0);

    // 绘制所有标注
    annotations.forEach(anno => {
      drawAnnotation(ctx, anno);
    });

    // 绘制当前正在绘制的标注
    if (currentAnnotation) {
      drawAnnotation(ctx, currentAnnotation);
    }
  }, [annotations, currentAnnotation]);

  // 绘制单个标注
  const drawAnnotation = (ctx: CanvasRenderingContext2D, anno: Annotation) => {
    ctx.strokeStyle = anno.color;
    ctx.fillStyle = anno.color;
    ctx.lineWidth = 2;
    ctx.font = '14px sans-serif';

    switch (anno.type) {
      case 'rect':
        if (anno.width !== undefined && anno.height !== undefined) {
          ctx.strokeRect(anno.x, anno.y, anno.width, anno.height);
        }
        break;

      case 'arrow':
        if (anno.x2 !== undefined && anno.y2 !== undefined) {
          drawArrow(ctx, anno.x, anno.y, anno.x2, anno.y2);
        }
        break;

      case 'text':
        if (anno.text) {
          ctx.fillText(anno.text, anno.x, anno.y);
        }
        break;

      case 'mosaic':
        // 简化版马赛克：绘制半透明矩形
        if (anno.width !== undefined && anno.height !== undefined) {
          ctx.fillStyle = 'rgba(0, 0, 0, 0.6)';
          ctx.fillRect(anno.x, anno.y, anno.width, anno.height);
        }
        break;

      case 'highlight':
        if (anno.x2 !== undefined && anno.y2 !== undefined) {
          ctx.strokeStyle = 'rgba(250, 173, 20, 0.8)';
          ctx.lineWidth = 20;
          ctx.lineCap = 'round';
          ctx.beginPath();
          ctx.moveTo(anno.x, anno.y);
          ctx.lineTo(anno.x2, anno.y2);
          ctx.stroke();
        }
        break;
    }
  };

  // 绘制箭头
  const drawArrow = (ctx: CanvasRenderingContext2D, fromX: number, fromY: number, toX: number, toY: number) => {
    const headLength = 10;
    const angle = Math.atan2(toY - fromY, toX - fromX);

    ctx.beginPath();
    ctx.moveTo(fromX, fromY);
    ctx.lineTo(toX, toY);
    ctx.stroke();

    ctx.beginPath();
    ctx.moveTo(toX, toY);
    ctx.lineTo(toX - headLength * Math.cos(angle - Math.PI / 6), toY - headLength * Math.sin(angle - Math.PI / 6));
    ctx.lineTo(toX - headLength * Math.cos(angle + Math.PI / 6), toY - headLength * Math.sin(angle + Math.PI / 6));
    ctx.closePath();
    ctx.fill();
  };

  // 鼠标事件处理
  const handleMouseDown = (e: React.MouseEvent<HTMLCanvasElement>) => {
    if (currentTool === 'none') return;

    const rect = canvasRef.current?.getBoundingClientRect();
    if (!rect) return;

    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;

    if (currentTool === 'text') {
      // 文字工具：弹出输入框
      const inputText = prompt('请输入标注文字：');
      if (inputText) {
        const newAnnotation: Annotation = {
          type: 'text',
          x,
          y,
          text: inputText,
          color: TOOL_COLORS['text']
        };
        setAnnotations(prev => [...prev, newAnnotation]);
      }
      return;
    }

    setIsDrawing(true);
    setStartPos({ x, y });

    const newAnnotation: Annotation = {
      type: currentTool,
      x,
      y,
      color: TOOL_COLORS[currentTool]
    };
    setCurrentAnnotation(newAnnotation);
  };

  const handleMouseMove = (e: React.MouseEvent<HTMLCanvasElement>) => {
    if (!isDrawing || currentTool === 'none') return;

    const rect = canvasRef.current?.getBoundingClientRect();
    if (!rect) return;

    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;

    if (currentTool === 'rect' || currentTool === 'mosaic') {
      setCurrentAnnotation(prev => prev ? {
        ...prev,
        width: x - startPos.x,
        height: y - startPos.y
      } : null);
    } else if (currentTool === 'arrow' || currentTool === 'highlight') {
      setCurrentAnnotation(prev => prev ? {
        ...prev,
        x2: x,
        y2: y
      } : null);
    }
  };

  const handleMouseUp = () => {
    if (!isDrawing || !currentAnnotation) return;

    setAnnotations(prev => [...prev, currentAnnotation]);
    setCurrentAnnotation(null);
    setIsDrawing(false);
  };

  // 插入到聊天
  const handleInsert = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const dataUrl = canvas.toDataURL('image/png');
    onInsert(dataUrl);
  };

  // 重绘
  useEffect(() => {
    drawCanvas();
  }, [drawCanvas]);

  return (
    <div className="screenshot-editor">
      <div className="screenshot-editor__toolbar">
        <div className="screenshot-editor__tools">
          <button
            className={`screenshot-editor__tool ${currentTool === 'rect' ? 'screenshot-editor__tool--active' : ''}`}
            onClick={() => setCurrentTool('rect')}
            title="矩形框"
          >
            ▢
          </button>
          <button
            className={`screenshot-editor__tool ${currentTool === 'arrow' ? 'screenshot-editor__tool--active' : ''}`}
            onClick={() => setCurrentTool('arrow')}
            title="箭头"
          >
            →
          </button>
          <button
            className={`screenshot-editor__tool ${currentTool === 'text' ? 'screenshot-editor__tool--active' : ''}`}
            onClick={() => setCurrentTool('text')}
            title="文字"
          >
            T
          </button>
          <button
            className={`screenshot-editor__tool ${currentTool === 'mosaic' ? 'screenshot-editor__tool--active' : ''}`}
            onClick={() => setCurrentTool('mosaic')}
            title="马赛克"
          >
            ▪▪▪
          </button>
          <button
            className={`screenshot-editor__tool ${currentTool === 'highlight' ? 'screenshot-editor__tool--active' : ''}`}
            onClick={() => setCurrentTool('highlight')}
            title="高亮"
          >
            ✎
          </button>
        </div>

        <div className="screenshot-editor__actions">
          <button className="screenshot-editor__btn screenshot-editor__btn--secondary" onClick={onCancel}>
            取消
          </button>
          <button className="screenshot-editor__btn screenshot-editor__btn--primary" onClick={handleInsert}>
            插入聊天
          </button>
        </div>
      </div>

      <div className="screenshot-editor__canvas-container">
        <canvas
          ref={canvasRef}
          className="screenshot-editor__canvas"
          onMouseDown={handleMouseDown}
          onMouseMove={handleMouseMove}
          onMouseUp={handleMouseUp}
          onMouseLeave={handleMouseUp}
        />
      </div>
    </div>
  );
}