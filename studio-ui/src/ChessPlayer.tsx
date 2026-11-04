import { useMemo, useState } from 'react';
import { Chess } from 'chess.js';

const symbols: Record<string, string> = {
  wk: '♔', wq: '♕', wr: '♖', wb: '♗', wn: '♘', wp: '♙',
  bk: '♚', bq: '♛', br: '♜', bb: '♝', bn: '♞', bp: '♟'
};

export default function ChessPlayer() {
  const [fen, setFen] = useState(() => new Chess().fen());
  const [selected, setSelected] = useState<string | null>(null);
  const [message, setMessage] = useState('');
  const chess = useMemo(() => new Chess(fen), [fen]);
  const targets = selected
    ? chess.moves({ square: selected as never, verbose: true }).map(move => move.to)
    : [];
  const board = chess.board();
  const status = chess.isCheckmate()
    ? 'Checkmate'
    : chess.isStalemate()
      ? 'Draw by stalemate'
      : chess.isDraw()
        ? 'Draw'
        : (chess.turn() === 'w' ? 'White' : 'Black') + ' to move' + (chess.isCheck() ? ' · Check' : '');

  const selectSquare = (square: string) => {
    if (chess.isGameOver()) return;
    const piece = chess.get(square as never);
    if (selected && targets.includes(square)) {
      try {
        const next = new Chess(fen);
        next.move({ from: selected as never, to: square as never, promotion: 'q' });
        setFen(next.fen());
        setSelected(null);
        setMessage('');
      } catch {
        setMessage('Illegal move.');
      }
    } else if (piece && piece.color === chess.turn()) {
      setSelected(square);
      setMessage('');
    } else {
      setSelected(null);
      setMessage('Select your piece, then touch a highlighted square.');
    }
  };

  return (
    <section style={{ padding: 14, borderTop: '1px solid #263044', background: '#0b111c' }}>
      <div style={{ textAlign: 'center', marginBottom: 10 }}>
        <b style={{ fontSize: 13 }}>♟ AL CHESS</b>
        <div style={{ color: '#e5e7eb', fontSize: 14, marginTop: 6 }}>{status}</div>
      </div>
      <div style={{ maxWidth: 520, margin: '0 auto' }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(8, minmax(0, 1fr))', border: '2px solid #596579', borderRadius: 5, overflow: 'hidden', touchAction: 'manipulation', userSelect: 'none' }}>
          {board.flatMap((rank, r) => rank.map((piece, c) => {
            const square = 'abcdefgh'[c] + (8 - r);
            const light = (r + c) % 2 === 0;
            const isSelected = selected === square;
            const target = targets.includes(square);
            return (
              <div key={square} role="button" aria-label={square} tabIndex={0}
                onClick={() => selectSquare(square)}
                onKeyDown={event => { if (event.key === 'Enter' || event.key === ' ') selectSquare(square); }}
                style={{ aspectRatio: '1', display: 'flex', alignItems: 'center', justifyContent: 'center', border: isSelected ? '3px solid #facc15' : 0, padding: 0, position: 'relative', background: isSelected ? '#b7a34a' : light ? '#f0d9b5' : '#b58863', color: piece?.color === 'w' ? '#fffdf4' : '#171717', fontSize: 'clamp(25px, 8vw, 48px)', lineHeight: 1, textShadow: piece?.color === 'w' ? '0 1px 2px #222' : '0 1px 1px #eee', cursor: 'pointer', touchAction: 'manipulation' }}>
                {piece ? symbols[piece.color + piece.type] : ''}
                {target && <span style={{ position: 'absolute', width: piece ? '90%' : '22%', height: piece ? '90%' : '22%', borderRadius: '50%', border: piece ? '4px solid #176b3a' : 0, background: piece ? 'transparent' : '#176b3a88', left: '50%', top: '50%', transform: 'translate(-50%,-50%)', pointerEvents: 'none' }} />}
              </div>
            );
          }))}
        </div>
        <div style={{ textAlign: 'center', color: message ? '#fca5a5' : '#aab5c5', marginTop: 12, fontSize: 12 }}>
          {message || 'Touch a piece, then touch its highlighted destination. No movement buttons.'}
        </div>
      </div>
    </section>
  );
}
