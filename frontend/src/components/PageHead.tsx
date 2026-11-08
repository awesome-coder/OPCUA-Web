import React from 'react';

export default function PageHead({
  title,
  description,
  extra,
}: {
  title: React.ReactNode;
  description?: React.ReactNode;
  extra?: React.ReactNode;
}) {
  return (
    <div className="page-head">
      <div>
        <h2>{title}</h2>
        {description ? <p>{description}</p> : null}
      </div>
      {extra ? <div>{extra}</div> : null}
    </div>
  );
}
