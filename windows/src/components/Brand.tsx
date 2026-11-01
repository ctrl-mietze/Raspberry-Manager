import React from 'react';
export function BerryMark({size=44}:{size?:number}){
  return <span className="brand-mark" style={{width:size,height:size,minWidth:size}}>
    <svg viewBox="0 0 64 64" width={size*.75} height={size*.75} aria-hidden="true">
      <path d="M30 16C20 9 17 6 18 4c8-1 14 3 16 10 4-8 10-10 17-9-1 8-7 12-17 13" fill="#8FC775"/>
      <g fill="#A30F47">
      <circle cx="22" cy="23" r="8"/><circle cx="41" cy="23" r="8"/>
      <circle cx="17" cy="35" r="8"/><circle cx="32" cy="35" r="9"/><circle cx="47" cy="35" r="8"/>
      <circle cx="24" cy="47" r="9"/><circle cx="39" cy="47" r="9"/>
      <circle cx="32" cy="55" r="7"/>
      </g>
      <g fill="#ED427C" opacity=".82"><circle cx="22" cy="23" r="5"/><circle cx="41" cy="23" r="5"/><circle cx="17" cy="35" r="5"/><circle cx="32" cy="35" r="6"/><circle cx="47" cy="35" r="5"/><circle cx="24" cy="47" r="5"/><circle cx="39" cy="47" r="5"/></g>
    </svg>
  </span>
}
export default BerryMark;
