import React from 'react';
import { Button, Card, Result } from 'antd';
import { useNavigate } from 'react-router-dom';

export default function NotFoundPage() {
  const navigate = useNavigate();
  return (
    <Card variant="borderless">
      <Result
        status="404"
        title="页面不存在"
        subTitle="地址可能已变更，或功能尚未开放。"
        extra={
          <Button type="primary" onClick={() => navigate('/')}>
            返回运行总览
          </Button>
        }
      />
    </Card>
  );
}
